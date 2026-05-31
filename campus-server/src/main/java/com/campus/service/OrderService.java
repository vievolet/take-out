package com.campus.service;

import com.campus.dto.OrdersSubmitDTO;
import com.campus.vo.OrderSubmitVO;


public interface OrderService {
    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);
}
