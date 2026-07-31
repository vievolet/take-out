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
     * 发送延时消息，expirationMillis 单位为毫秒
     */
    public void sendDelayOrder(Long orderId, long expirationMillis) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.ORDER_EXCHANGE,
                RabbitConfig.ORDER_DELAY_ROUTING_KEY,
                orderId,
                (Message message) -> {
                    message.getMessageProperties().setExpiration(String.valueOf(expirationMillis));
                    message.getMessageProperties().setHeader("orderId", orderId);
                    return message;
                }
        );
    }
}

