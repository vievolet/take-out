package com.campus.service;

import com.campus.vo.OrderReportVO;
import com.campus.vo.SalesTop10ReportVO;
import com.campus.vo.TurnoverReportVO;
import com.campus.vo.UserReportVO;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;

public interface ReportService {

    /**
     * 营业额统计（按天，区间内）
     */
    TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end);

    /**
     * 用户统计（按天新增 + 累计总量）
     */
    UserReportVO getUserStatistics(LocalDate begin, LocalDate end);

    /**
     * 订单统计（按天订单数/有效订单数 + 汇总）
     */
    OrderReportVO getOrdersStatistics(LocalDate begin, LocalDate end);

    /**
     * 销量排名 Top10
     */
    SalesTop10ReportVO getTop10(LocalDate begin, LocalDate end);

    /**
     * 导出最近 30 天运营数据报表（xlsx，直接写响应流）
     */
    void exportBusinessData(HttpServletResponse response);
}
