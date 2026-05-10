package com.dbhelp.generate.rabbit;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

/**
 * 造数 RabbitMQ：仅在 {@code dbhelp.generate.rabbit.enabled=true} 时装配，避免无 Broker 时误连。
 */
@Configuration
@EnableRabbit
@ConditionalOnProperty(prefix = "dbhelp.generate.rabbit", name = "enabled", havingValue = "true")
public class GenerateRabbitMqConfig {

    @Bean
    public ConnectionFactory generateRabbitConnectionFactory(
            @Value("${dbhelp.generate.rabbit.host}") String host,
            @Value("${dbhelp.generate.rabbit.port}") int port,
            @Value("${dbhelp.generate.rabbit.username}") String username,
            @Value("${dbhelp.generate.rabbit.password}") String password,
            @Value("${dbhelp.generate.rabbit.virtual-host:/}") String virtualHost) {
        CachingConnectionFactory cf = new CachingConnectionFactory(host);
        cf.setPort(port);
        cf.setUsername(username);
        cf.setPassword(password);
        cf.setVirtualHost(virtualHost);
        return cf;
    }

    @Bean
    public MessageConverter generateJacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory generateRabbitConnectionFactory,
                                         MessageConverter generateJacksonMessageConverter) {
        RabbitTemplate t = new RabbitTemplate(generateRabbitConnectionFactory);
        t.setMessageConverter(generateJacksonMessageConverter);
        return t;
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory generateRabbitConnectionFactory) {
        return new RabbitAdmin(generateRabbitConnectionFactory);
    }

    @Bean
    public Queue generateBatchQueue(@Value("${dbhelp.generate.rabbit.queue}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean(name = "rabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory generateRabbitConnectionFactory,
            MessageConverter generateJacksonMessageConverter) {
        SimpleRabbitListenerContainerFactory f = new SimpleRabbitListenerContainerFactory();
        f.setConnectionFactory(generateRabbitConnectionFactory);
        f.setMessageConverter(generateJacksonMessageConverter);
        f.setConcurrentConsumers(1);
        f.setMaxConcurrentConsumers(1);
        f.setPrefetchCount(1);
        f.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return f;
    }
}
