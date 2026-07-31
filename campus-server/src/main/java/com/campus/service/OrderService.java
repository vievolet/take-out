package com.campus.service;

import com.campus.dto.OrdersSubmitDTO;
import com.campus.vo.OrderSubmitVO;


public interface OrderService {
    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);

    /**
     * 处理订单超时（由延时队列触发）
     * @param orderId 订单 id
     */
    void handleTimeout(Long orderId);
}
