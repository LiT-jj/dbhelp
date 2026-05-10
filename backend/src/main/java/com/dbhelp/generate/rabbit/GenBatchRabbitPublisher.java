package com.dbhelp.generate.rabbit;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 将单批造数消息投递到配置的队列。
 */
@Component
@ConditionalOnProperty(prefix = "dbhelp.generate.rabbit", name = "enabled", havingValue = "true")
public class GenBatchRabbitPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String queueName;

    public GenBatchRabbitPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${dbhelp.generate.rabbit.queue}") String queueName) {
        this.rabbitTemplate = rabbitTemplate;
        this.queueName = queueName;
    }

    public void publish(GenBatchMessage message) {
        rabbitTemplate.convertAndSend(queueName, message);
    }
}
