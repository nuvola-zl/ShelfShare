package com.shelf.gateway.config;

import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.stp.StpUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shelf.gateway.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class SaTokenGatewayConfig {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Bean
    public SaReactorFilter saReactorFilter() {
        return new SaReactorFilter()
                .addInclude("/**")
                .addExclude("/api/auth/login")
                .addExclude("/api/auth/register")
                .addExclude("/api/auth/captcha")
                .addExclude("/api/public/**")
                .addExclude("/api/donate/sku/search")
                .addExclude("/api/donate/course/**")
                .addExclude("/ws/**")
                .addExclude("/favicon.ico")
                .setAuth(obj -> StpUtil.checkLogin())
                .setError(e -> {
                    log.warn("网关鉴权拦截: {}", e.getMessage());
                    try {
                        Result result = Result.fail(401, "登录已过期，请重新登录");
                        return mapper.writeValueAsString(result);
                    } catch (Exception ex) {
                        return "{\"code\":401,\"message\":\"登录已过期，请重新登录\",\"data\":null}";
                    }
                });
    }
}