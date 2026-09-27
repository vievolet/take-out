package com.campus.queue;

import com.campus.config.RabbitConfig;
import com.campus.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
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
            // 关单失败仅记录日志；handleTimeout 本身是单条条件更新，失败影响面可控。
            // 生产化扩展点：失败消息投递真正的 DLQ + 定时对账扫描兜底（当前按比例原则不引入）
            log.error("订单超时处理失败：orderId={}", orderId, e);
        }
    }
}

