package com.example.chatgateway.global.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class AuthFilter implements GatewayFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        log.info("요청이 들어온 경로: {}", path);

        // 예외 처리(로그인 및 회원가입)
        if (path.startsWith("/api/users/login") || path.startsWith("/api/users/signup")) {
            return chain.filter(exchange);
        }

        // 다음 단계로 넘기기
        return chain.filter(exchange);
    }
}
