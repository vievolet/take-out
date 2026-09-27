package com.campus.service;

import com.campus.vo.BusinessDataVO;
import com.campus.vo.DishOverViewVO;
import com.campus.vo.OrderOverViewVO;
import com.campus.vo.SetmealOverViewVO;

import java.time.LocalDateTime;

public interface WorkspaceService {

    /**
     * 查询今日运营数据（时间段内营业额/有效订单/完成率/客单价/新增用户）
     * @param begin
     * @param end
     * @return
     */
    BusinessDataVO getBusinessData(LocalDateTime begin, LocalDateTime end);

    /**
     * 菜品总览（起售/停售数量）
     * @return
     */
    DishOverViewVO getDishOverView();

    /**
     * 套餐总览（起售/停售数量）
     * @return
     */
    SetmealOverViewVO getSetmealOverView();

    /**
     * 订单概览（各状态订单数量）
     * @return
     */
    OrderOverViewVO getOrderOverView();
}
