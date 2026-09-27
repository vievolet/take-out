package com.campus.service;

import com.campus.dto.OrdersCancelDTO;
import com.campus.dto.OrdersConfirmDTO;
import com.campus.dto.OrdersPageQueryDTO;
import com.campus.dto.OrdersPaymentDTO;
import com.campus.dto.OrdersRejectionDTO;
import com.campus.dto.OrdersSubmitDTO;
import com.campus.result.PageResult;
import com.campus.vo.OrderPaymentVO;
import com.campus.vo.OrderStatisticsVO;
import com.campus.vo.OrderSubmitVO;
import com.campus.vo.OrderVO;


public interface OrderService {
    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);

    /**
     * 处理订单超时（由延时队列触发）
     * @param orderId 订单 id
     */
    void handleTimeout(Long orderId);

    /**
     * 订单支付（生成调起支付参数；mock 模式下按配置可立即置为支付成功）
     * @param ordersPaymentDTO
     * @return
     */
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO);

    /**
     * 支付成功（模拟微信支付回调入口，真实模式对应 notify 回调处理器）
     * @param orderNumber 订单号
     */
    void paymentSuccess(String orderNumber);

    /**
     * 用户端历史订单分页查询
     * @param ordersPageQueryDTO
     * @return
     */
    PageResult historyOrders(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 用户端查询订单详情（校验归属）
     * @param id 订单id
     * @return
     */
    OrderVO orderDetail(Long id);

    /**
     * 用户取消订单（待付款/待接单/已接单/派送中可取消，已支付走退款）
     * @param ordersCancelDTO
     */
    void userCancel(OrdersCancelDTO ordersCancelDTO);

    /**
     * 用户催单（仅已接单/派送中可催）
     * @param id 订单id
     */
    void reminder(Long id);

    /**
     * 再来一单：按订单明细快照重新加入购物车
     * @param id 订单id
     */
    void repetition(Long id);

    /**
     * 管理端条件分页查询订单
     * @param ordersPageQueryDTO
     * @return
     */
    PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 管理端订单各状态数量统计
     * @return
     */
    OrderStatisticsVO statistics();

    /**
     * 管理端查询订单详情
     * @param id 订单id
     * @return
     */
    OrderVO details(Long id);

    /**
     * 管理端接单：待接单 -> 已接单
     * @param ordersConfirmDTO
     */
    void adminConfirm(OrdersConfirmDTO ordersConfirmDTO);

    /**
     * 管理端拒单：仅待接单且已支付的订单可拒单，退款并取消
     * @param ordersRejectionDTO
     */
    void adminRejection(OrdersRejectionDTO ordersRejectionDTO);

    /**
     * 管理端取消订单：待接单/已接单/派送中可取消，已支付走退款
     * @param ordersCancelDTO
     */
    void adminCancel(OrdersCancelDTO ordersCancelDTO);

    /**
     * 管理端派送：已接单 -> 派送中
     * @param id 订单id
     */
    void delivery(Long id);

    /**
     * 管理端完成：派送中 -> 已完成
     * @param id 订单id
     */
    void complete(Long id);
}
