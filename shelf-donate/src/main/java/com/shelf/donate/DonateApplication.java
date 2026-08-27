package com.shelf.donate;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.shelf.api")
@MapperScan("com.shelf.donate.mapper")
@SpringBootApplication(scanBasePackages = {"com.shelf.donate", "com.shelf.common"})
public class DonateApplication {
    public static void main(String[] args) {
        SpringApplication.run(DonateApplication.class, args);
    }
}