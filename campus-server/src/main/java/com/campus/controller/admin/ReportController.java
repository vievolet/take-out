package com.campus.controller.admin;

import com.campus.result.Result;
import com.campus.service.ReportService;
import com.campus.vo.OrderReportVO;
import com.campus.vo.SalesTop10ReportVO;
import com.campus.vo.TurnoverReportVO;
import com.campus.vo.UserReportVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;

/**
 * 管理端数据统计报表
 */
@RestController
@RequestMapping("/admin/report")
@Api(tags = "数据统计相关接口")
@Slf4j
public class ReportController {

    @Autowired
    private ReportService reportService;

    /**
     * 营业额统计
     * @param begin 开始日期
     * @param end   结束日期（未传时默认当天）
     * @return
     */
    @GetMapping("/turnoverStatistics")
    @ApiOperation("营业额统计")
    public Result<TurnoverReportVO> turnoverStatistics(
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end){
        LocalDate[] range = defaultRange(begin, end);
        return Result.success(reportService.getTurnoverStatistics(range[0], range[1]));
    }

    /**
     * 用户统计
     */
    @GetMapping("/userStatistics")
    @ApiOperation("用户统计")
    public Result<UserReportVO> userStatistics(
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end){
        LocalDate[] range = defaultRange(begin, end);
        return Result.success(reportService.getUserStatistics(range[0], range[1]));
    }

    /**
     * 订单统计
     */
    @GetMapping("/ordersStatistics")
    @ApiOperation("订单统计")
    public Result<OrderReportVO> ordersStatistics(
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end){
        LocalDate[] range = defaultRange(begin, end);
        return Result.success(reportService.getOrdersStatistics(range[0], range[1]));
    }

    /**
     * 销量排名Top10
     */
    @GetMapping("/top10")
    @ApiOperation("销量排名Top10")
    public Result<SalesTop10ReportVO> top10(
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end){
        LocalDate[] range = defaultRange(begin, end);
        return Result.success(reportService.getTop10(range[0], range[1]));
    }

    /**
     * 导出最近 30 天运营数据报表（Excel）
     */
    @GetMapping("/export")
    @ApiOperation("导出运营数据报表")
    public void export(HttpServletResponse response){
        reportService.exportBusinessData(response);
    }

    /**
     * 参数缺省：未传时间时默认查询当天
     */
    private LocalDate[] defaultRange(LocalDate begin, LocalDate end) {
        if (begin == null) {
            begin = LocalDate.now();
        }
        if (end == null) {
            end = begin;
        }
        return new LocalDate[]{begin, end};
    }
}
