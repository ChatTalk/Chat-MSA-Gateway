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

    @Value("${uri.message}")
    private String message;

    @Value("${uri.ws}")
    private String ws;

    @Value("${uri.participant}")
    private String participant;

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
                .route("message", r -> r.path("/stomp/chat/**")
                        .filters(f -> f.filter(authFilter))
                        .uri(message))
//                .route("ws", r -> r.path("/stomp/chat/**")
//                        .uri(ws))
                .route("participant", r -> r.path("/stomp/participant/**")
                        .filters(f -> f.filter(authFilter))
                        .uri(participant))
                .build();
    }


}
