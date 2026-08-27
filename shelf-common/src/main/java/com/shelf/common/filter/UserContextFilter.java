package com.shelf.common.filter;

import com.shelf.common.context.UserContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
public class UserContextFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        try {
            String userId = httpRequest.getHeader("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                UserContext.setUserId(Long.valueOf(userId));
            }

            String role = httpRequest.getHeader("X-User-Role");
            if (role != null && !role.isBlank()) {
                UserContext.setRole(role);
            }

            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }
}