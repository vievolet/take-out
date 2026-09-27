package com.campus.service.impl;

import com.campus.mapper.ReportMapper;
import com.campus.service.WorkspaceService;
import com.campus.vo.BusinessDataVO;
import com.campus.vo.DishOverViewVO;
import com.campus.vo.OrderOverViewVO;
import com.campus.vo.SetmealOverViewVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class WorkspaceServiceImpl implements WorkspaceService {

    @Autowired
    private ReportMapper reportMapper;

    /**
     * 查询今日运营数据
     */
    @Override
    public BusinessDataVO getBusinessData(LocalDateTime begin, LocalDateTime end) {
        Double turnover = reportMapper.sumTurnover(begin, end);
        Integer validOrderCount = reportMapper.countValidOrders(begin, end);
        Integer totalOrderCount = reportMapper.countTotalOrders(begin, end);
        Integer newUsers = reportMapper.countNewUsers(begin, end);

        // 除零保护：无订单时完成率/客单价返回 0
        double orderCompletionRate = totalOrderCount == null || totalOrderCount == 0
                ? 0.0 : validOrderCount * 1.0 / totalOrderCount;
        double unitPrice = validOrderCount == null || validOrderCount == 0
                ? 0.0 : turnover / validOrderCount;

        return BusinessDataVO.builder()
                .turnover(turnover)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .unitPrice(unitPrice)
                .newUsers(newUsers)
                .build();
    }

    @Override
    public DishOverViewVO getDishOverView() {
        return reportMapper.dishOverView();
    }

    @Override
    public SetmealOverViewVO getSetmealOverView() {
        return reportMapper.setmealOverView();
    }

    @Override
    public OrderOverViewVO getOrderOverView() {
        return reportMapper.orderOverView();
    }
}
