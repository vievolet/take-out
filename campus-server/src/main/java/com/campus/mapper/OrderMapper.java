package com.campus.mapper;

import com.campus.dto.OrdersPageQueryDTO;
import com.campus.entity.Orders;
import com.campus.vo.OrderStatisticsVO;
import com.github.pagehelper.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface OrderMapper {

    void insert(Orders orders);

    /**
     * 根据id查询订单
     * @param id
     * @return
     */
    Orders getById(Long id);

    /**
     * 根据订单号查询订单
     * @param number
     * @return
     */
    Orders getByNumber(String number);

    /**
     * 分页条件查询（只查 orders 单表，明细通过 order_detail 批量 in 查询，避免 join 破坏 PageHelper 的 count）
     * @param ordersPageQueryDTO
     * @return
     */
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 将未支付且处于待付款状态的订单更新为取消状态，返回影响行数（用于幂等判断）
     */
    int updateStatusToCancelledIfUnpaid(@Param("id") Long id,
                                        @Param("cancelTime") LocalDateTime cancelTime);

    /**
     * 支付成功：待付款(1) + 未支付(0) 条件更新为 待接单(2) + 已支付(1)，条件更新保证并发下只有一次生效（幂等）
     * @return 影响行数
     */
    int markPaid(@Param("id") Long id,
                 @Param("checkoutTime") LocalDateTime checkoutTime,
                 @Param("payMethod") Integer payMethod);

    /**
     * 带期望状态的动态更新：状态流转统一入口，期望状态不匹配则影响行数为 0，
     * 用于防止并发/重复操作（类似乐观锁，M1 各流转接口 + 延时关单竞态都靠它保证）
     * @param orders       需要更新的字段
     * @param expectStatus 期望的当前状态，null 表示不校验
     * @return 影响行数
     */
    int updateWithGuard(@Param("orders") Orders orders, @Param("expectStatus") Integer expectStatus);

    /**
     * 订单各状态数量统计（待接单/待派送/派送中）
     * @return
     */
    OrderStatisticsVO statistics();
}
