package com.example.chatgateway.global.config;

import com.example.chatgateway.global.filter.AuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@RequiredArgsConstructor
public class RoutesConfig {

    @Value("${uri.user}")
    private String user;

    @Value("${uri.chat}")
    private String chat;

    private final AuthFilter authFilter;

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {

        return builder.routes()
                .route("user", r -> r.path("/api/users/**")
                        .filters(f -> f.filter(authFilter))
                        .uri(user))
                .route("chat", r -> r.path("/api/open-chats/**")
                        .filters(f -> f.filter(authFilter))
                        .uri(chat))
                .build();
    }
}
