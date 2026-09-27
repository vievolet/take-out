package com.campus.mapper;

import com.campus.dto.GoodsSalesDTO;
import com.campus.vo.DishOverViewVO;
import com.campus.vo.OrderOverViewVO;
import com.campus.vo.SetmealOverViewVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 统计报表聚合查询。
 *
 * 统计口径（与面试话术保持一致）：
 *   营业额/有效订单  -> 按 checkout_time（支付时间，管理端直接完成的订单在 complete 时回填）
 *   总订单           -> 按 order_time（下单时间）
 *   新增用户         -> 按 create_time
 */
@Mapper
public interface ReportMapper {

    /** 区间营业额：已完成的订单实收金额之和 */
    Double sumTurnover(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end);

    /** 区间有效订单数（已完成） */
    Integer countValidOrders(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end);

    /** 区间总订单数 */
    Integer countTotalOrders(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end);

    /** 区间新增用户数 */
    Integer countNewUsers(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end);

    /** 区间开始前的用户总量（做累计曲线的基数） */
    Integer totalUsersBefore(@Param("begin") LocalDateTime begin);

    /** 按天营业额：key = date(日期) / turnover(营业额) */
    List<Map<String, Object>> turnoverByDate(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end);

    /** 按天新增用户：key = date / num */
    List<Map<String, Object>> newUsersByDate(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end);

    /** 按天订单数：key = date / orderCount / validCount */
    List<Map<String, Object>> ordersByDate(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end);

    /** 销量 Top10（按已完成订单明细聚合） */
    List<GoodsSalesDTO> salesTop10(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end);

    /** 工作台：订单各状态概览 */
    OrderOverViewVO orderOverView();

    /** 工作台：菜品起售/停售概览 */
    DishOverViewVO dishOverView();

    /** 工作台：套餐起售/停售概览 */
    SetmealOverViewVO setmealOverView();
}
