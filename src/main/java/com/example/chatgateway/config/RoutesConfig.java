package com.example.chatgateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RoutesConfig {

    @Value("${uri.user}")
    private String user;

    @Value("${uri.chat}")
    private String chat;

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {

        return builder.routes()
                .route("user", r -> r.path("/api/users/**").uri(user))
                .route("chat", r -> r.path("/api/open-chats/**").uri(chat))
                .build();
    }
}
