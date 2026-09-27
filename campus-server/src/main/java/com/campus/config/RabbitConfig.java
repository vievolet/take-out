package com.campus.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String ORDER_DELAY_QUEUE = "order.delay.queue";
    public static final String ORDER_DELAY_ROUTING_KEY = "order.delay.key";
    public static final String ORDER_TIMEOUT_QUEUE = "order.timeout.queue";
    public static final String ORDER_TIMEOUT_ROUTING_KEY = "order.timeout.key";

    /** 订单支付超时时间（毫秒），作为延时队列的队列级 TTL */
    @Value("${campus.order.pay-timeout-ms:1800000}")
    private long payTimeoutMs;

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE, true, false);
    }

    /**
     * 延时队列：TTL 到期后消息成为死信，按 DLX 路由键转发到 order.timeout.queue。
     *
     * 使用【队列级 TTL】而非消息级 TTL：队列内所有消息同一 TTL，FIFO 顺序即过期顺序，
     * 不会出现消息级 TTL 混用不同过期时间时的队头阻塞问题（踩坑记录：某条长 TTL 消息堵在
     * 队头时，后面的短 TTL 消息到期也无法被移出队列）。
     *
     * 注意：TTL 是队列属性，修改 campus.order.pay-timeout-ms 后需删除旧队列重建
     * （rabbitmqctl delete_queue order.delay.queue），否则参数不一致会声明失败。
     * 更通用的方案是 rabbitmq_delayed_message_exchange 插件（支持每条消息独立延迟），
     * 项目当前规模按队列级 TTL 即可。
     */
    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable(ORDER_DELAY_QUEUE)
                .withArgument("x-message-ttl", payTimeoutMs)
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

