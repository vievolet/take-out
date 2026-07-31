package com.campus.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class  RabbitConfig {

    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String ORDER_DELAY_QUEUE = "order.delay.queue";
    public static final String ORDER_DELAY_ROUTING_KEY = "order.delay.key";
    public static final String ORDER_TIMEOUT_QUEUE = "order.timeout.queue";
    public static final String ORDER_TIMEOUT_ROUTING_KEY = "order.timeout.key";

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE, true, false);
    }

    /**
     * Delay queue: messages will be dead-lettered to the exchange with the timeout routing key when TTL expires.
     * We set DLX routing args here; message-level TTL is used when sending to this queue.
     */
    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable(ORDER_DELAY_QUEUE)
                .withArgument("x-dead-letter-exchange", ORDER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ORDER_TIMEOUT_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue orderTimeoutQueue() {
        return QueueBuilder.durable(ORDER_TIMEOUT_QUEUE).build();
    }

    @Bean
    public Binding bindingDelayQueue() {
        return BindingBuilder.bind(orderDelayQueue()).to(orderExchange()).with(ORDER_DELAY_ROUTING_KEY);
    }

    @Bean
    public Binding bindingTimeoutQueue() {
        return BindingBuilder.bind(orderTimeoutQueue()).to(orderExchange()).with(ORDER_TIMEOUT_ROUTING_KEY);
    }
}

