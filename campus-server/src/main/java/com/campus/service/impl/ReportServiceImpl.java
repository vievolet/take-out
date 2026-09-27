package com.campus.service.impl;

import com.campus.dto.GoodsSalesDTO;
import com.campus.mapper.ReportMapper;
import com.campus.service.ReportService;
import com.campus.vo.OrderReportVO;
import com.campus.vo.SalesTop10ReportVO;
import com.campus.vo.TurnoverReportVO;
import com.campus.vo.UserReportVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    @Autowired
    private ReportMapper reportMapper;

    /**
     * 营业额统计（按天）
     */
    @Override
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dates = buildDateRange(begin, end);
        List<Map<String, Object>> rows = reportMapper.turnoverByDate(
                LocalDateTime.of(begin, LocalTime.MIN), LocalDateTime.of(end, LocalTime.MAX));
        Map<String, Double> turnoverMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            turnoverMap.put((String) row.get("date"), toDouble(row.get("turnover")));
        }

        List<String> dateList = new ArrayList<>();
        List<String> turnoverList = new ArrayList<>();
        for (LocalDate date : dates) {
            dateList.add(date.toString());
            // 无数据的日期补 0
            turnoverList.add(String.valueOf(turnoverMap.getOrDefault(date.toString(), 0.0)));
        }
        return TurnoverReportVO.builder()
                .dateList(String.join(",", dateList))
                .turnoverList(String.join(",", turnoverList))
                .build();
    }

    /**
     * 用户统计（新增 + 累计总量）
     */
    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dates = buildDateRange(begin, end);
        // 区间开始前的用户总量作为累计曲线基数
        Integer base = reportMapper.totalUsersBefore(LocalDateTime.of(begin, LocalTime.MIN));
        List<Map<String, Object>> rows = reportMapper.newUsersByDate(
                LocalDateTime.of(begin, LocalTime.MIN), LocalDateTime.of(end, LocalTime.MAX));
        Map<String, Integer> newUserMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            newUserMap.put((String) row.get("date"), toInt(row.get("num")));
        }

        List<String> dateList = new ArrayList<>();
        List<String> totalUserList = new ArrayList<>();
        List<String> newUserList = new ArrayList<>();
        int total = base == null ? 0 : base;
        for (LocalDate date : dates) {
            int newUsers = newUserMap.getOrDefault(date.toString(), 0);
            total += newUsers;
            dateList.add(date.toString());
            totalUserList.add(String.valueOf(total));
            newUserList.add(String.valueOf(newUsers));
        }
        return UserReportVO.builder()
                .dateList(String.join(",", dateList))
                .totalUserList(String.join(",", totalUserList))
                .newUserList(String.join(",", newUserList))
                .build();
    }

    /**
     * 订单统计（按天订单数/有效订单数 + 汇总）
     */
    @Override
    public OrderReportVO getOrdersStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dates = buildDateRange(begin, end);
        List<Map<String, Object>> rows = reportMapper.ordersByDate(
                LocalDateTime.of(begin, LocalTime.MIN), LocalDateTime.of(end, LocalTime.MAX));
        Map<String, Integer> orderCountMap = new HashMap<>();
        Map<String, Integer> validCountMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String date = (String) row.get("date");
            orderCountMap.put(date, toInt(row.get("orderCount")));
            validCountMap.put(date, toInt(row.get("validCount")));
        }

        List<String> dateList = new ArrayList<>();
        List<String> orderCountList = new ArrayList<>();
        List<String> validOrderCountList = new ArrayList<>();
        int totalOrderCount = 0;
        int validOrderCount = 0;
        for (LocalDate date : dates) {
            int orderCount = orderCountMap.getOrDefault(date.toString(), 0);
            int validCount = validCountMap.getOrDefault(date.toString(), 0);
            totalOrderCount += orderCount;
            validOrderCount += validCount;
            dateList.add(date.toString());
            orderCountList.add(String.valueOf(orderCount));
            validOrderCountList.add(String.valueOf(validCount));
        }
        double orderCompletionRate = totalOrderCount == 0 ? 0.0 : validOrderCount * 1.0 / totalOrderCount;

        return OrderReportVO.builder()
                .dateList(String.join(",", dateList))
                .orderCountList(String.join(",", orderCountList))
                .validOrderCountList(String.join(",", validOrderCountList))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
    }

    /**
     * 销量排名 Top10
     */
    @Override
    public SalesTop10ReportVO getTop10(LocalDate begin, LocalDate end) {
        List<GoodsSalesDTO> goodsSalesList = reportMapper.salesTop10(
                LocalDateTime.of(begin, LocalTime.MIN), LocalDateTime.of(end, LocalTime.MAX));

        String nameList = goodsSalesList.stream()
                .map(GoodsSalesDTO::getName).collect(Collectors.joining(","));
        String numberList = goodsSalesList.stream()
                .map(d -> String.valueOf(d.getNumber())).collect(Collectors.joining(","));
        return SalesTop10ReportVO.builder()
                .nameList(nameList)
                .numberList(numberList)
                .build();
    }

    /**
     * 导出最近 30 天运营数据报表。
     *
     * 选型说明：30 天 x 4 张表数据量很小，用 POI 的 XSSFWorkbook 最简单；
     * 若数据量上万行会改用 SXSSFWorkbook（流式写入固定内存窗口，防止 OOM，POI 3.16 已内置）。
     */
    @Override
    public void exportBusinessData(HttpServletResponse response) {
        LocalDate begin = LocalDate.now().minusDays(29);
        LocalDate end = LocalDate.now();

        TurnoverReportVO turnover = getTurnoverStatistics(begin, end);
        UserReportVO user = getUserStatistics(begin, end);
        OrderReportVO order = getOrdersStatistics(begin, end);
        SalesTop10ReportVO top10 = getTop10(begin, end);

        String[] dateList = turnover.getDateList().split(",");
        String[] turnoverList = turnover.getTurnoverList().split(",");
        String[] totalUserList = user.getTotalUserList().split(",");
        String[] newUserList = user.getNewUserList().split(",");
        String[] orderCountList = order.getOrderCountList().split(",");
        String[] validOrderCountList = order.getValidOrderCountList().split(",");
        String[] nameList = top10.getNameList().split(",");
        String[] numberList = top10.getNumberList().split(",");

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // Sheet1 营业额统计
            XSSFSheet turnoverSheet = workbook.createSheet("营业额统计");
            addRow(turnoverSheet, 0, "时间", "营业额");
            for (int i = 0; i < dateList.length; i++) {
                addRow(turnoverSheet, i + 1, dateList[i], Double.parseDouble(turnoverList[i]));
            }

            // Sheet2 用户统计
            XSSFSheet userSheet = workbook.createSheet("用户统计");
            addRow(userSheet, 0, "日期", "用户总量", "新增用户");
            for (int i = 0; i < dateList.length; i++) {
                addRow(userSheet, i + 1, dateList[i], Integer.parseInt(totalUserList[i]), Integer.parseInt(newUserList[i]));
            }

            // Sheet3 订单统计
            XSSFSheet orderSheet = workbook.createSheet("订单统计");
            addRow(orderSheet, 0, "日期", "订单数", "有效订单数");
            for (int i = 0; i < dateList.length; i++) {
                addRow(orderSheet, i + 1, dateList[i], Integer.parseInt(orderCountList[i]), Integer.parseInt(validOrderCountList[i]));
            }
            // 汇总行
            int summaryRow = dateList.length + 2;
            addRow(orderSheet, summaryRow, "订单总数", order.getTotalOrderCount());
            addRow(orderSheet, summaryRow + 1, "有效订单数", order.getValidOrderCount());
            addRow(orderSheet, summaryRow + 2, "订单完成率", order.getOrderCompletionRate());

            // Sheet4 销量排名Top10
            XSSFSheet top10Sheet = workbook.createSheet("销量排名Top10");
            addRow(top10Sheet, 0, "排名", "商品名称", "销量");
            for (int i = 0; i < nameList.length; i++) {
                addRow(top10Sheet, i + 1, i + 1, nameList[i], Integer.parseInt(numberList[i]));
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",
                    "attachment; filename=" + URLEncoder.encode("运营数据报表.xlsx", "UTF-8"));
            workbook.write(response.getOutputStream());
        } catch (IOException e) {
            log.error("导出运营数据报表失败", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 写一行单元格，按类型区分字符串/数值，便于 Excel 中直接求和
     */
    private void addRow(XSSFSheet sheet, int rowIdx, Object... cells) {
        XSSFRow row = sheet.createRow(rowIdx);
        for (int i = 0; i < cells.length; i++) {
            Object cell = cells[i];
            if (cell instanceof String) {
                row.createCell(i).setCellValue((String) cell);
            } else if (cell instanceof Double) {
                row.createCell(i).setCellValue((Double) cell);
            } else if (cell instanceof Integer) {
                row.createCell(i).setCellValue((Integer) cell);
            }
        }
    }

    /**
     * 生成闭区间日期列表（含 begin 和 end）
     */
    private List<LocalDate> buildDateRange(LocalDate begin, LocalDate end) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = begin; !date.isAfter(end); date = date.plusDays(1)) {
            dates.add(date);
        }
        return dates;
    }

    private double toDouble(Object value) {
        return value == null ? 0.0 : ((Number) value).doubleValue();
    }

    private int toInt(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }
}
