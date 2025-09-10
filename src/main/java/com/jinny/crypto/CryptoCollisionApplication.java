package com.jinny.crypto;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@ComponentScan(basePackages = {"com.jinny"})
@MapperScan("com.jinny.crypto.modules.**.mapper")
public class CryptoCollisionApplication {

    public static void main(String[] args) {
        SpringApplication.run(CryptoCollisionApplication.class, args);
    }

}
