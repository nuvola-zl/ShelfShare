package com.shelf.common.oss;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AliOssProperties.class)
@ConditionalOnProperty(prefix = "shelf.oss", name = "endpoint")
public class OssAutoConfiguration {

    @Bean(destroyMethod = "destroy")
    public AliOssTemplate aliOssTemplate(AliOssProperties properties) {
        return new AliOssTemplate(properties);
    }
}