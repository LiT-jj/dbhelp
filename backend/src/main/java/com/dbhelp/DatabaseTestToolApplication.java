package com.dbhelp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;

@SpringBootApplication(exclude = RabbitAutoConfiguration.class)
@MapperScan("com.dbhelp.mapper")
public class DatabaseTestToolApplication {
    public static void main(String[] args) {
        SpringApplication.run(DatabaseTestToolApplication.class, args);
    }
}
