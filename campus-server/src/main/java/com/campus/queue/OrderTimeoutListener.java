package com.campus.queue;

import com.campus.config.RabbitConfig;
import com.campus.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderTimeoutListener {

    private final OrderService orderService;

    public OrderTimeoutListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = RabbitConfig.ORDER_TIMEOUT_QUEUE)
    public void handleOrderTimeout(Long orderId) {
        if (orderId == null) return;
        try {
            orderService.handleTimeout(orderId);
        } catch (Exception e) {
            // TODO: 考虑重试或将消息丢到 DLQ；此处简单打印
            e.printStackTrace();
        }
    }
}

