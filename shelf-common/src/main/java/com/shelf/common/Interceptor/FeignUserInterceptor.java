package com.shelf.common.Interceptor;

import com.shelf.common.context.UserContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;

@Component
public class FeignUserInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        Long userId = UserContext.getUserId();
        if (userId != null) {
            template.header("X-User-Id", String.valueOf(userId));
        }
        String role = UserContext.getRole();
        if (role != null) {
            template.header("X-User-Role", role);
        }
    }
}