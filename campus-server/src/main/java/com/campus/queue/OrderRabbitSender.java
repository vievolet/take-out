package com.campus.queue;

import com.campus.config.RabbitConfig;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderRabbitSender {

    private final RabbitTemplate rabbitTemplate;

    public OrderRabbitSender(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 发送延时消息到延时队列。
     * TTL 由队列级 x-message-ttl 统一控制（见 RabbitConfig），此处不设置消息级过期时间——
     * 混用消息级 TTL 会导致队头阻塞：长 TTL 消息堵在队头时，后续短 TTL 消息到期也无法移出队列。
     */
    public void sendDelayOrder(Long orderId) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.ORDER_EXCHANGE,
                RabbitConfig.ORDER_DELAY_ROUTING_KEY,
                orderId,
                (Message message) -> {
                    message.getMessageProperties().setHeader("orderId", orderId);
                    return message;
                }
        );
    }
}

