package com.example.chatgateway.global.filter;

import com.example.chatgateway.domain.dto.TokenDTO;
import com.example.chatgateway.domain.dto.UserInfoDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;

import java.time.Duration;
import java.util.UUID;

import static com.example.chatgateway.global.constant.Constants.COOKIE_AUTH_HEADER;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFilter implements GatewayFilter {

    @Value("${kafka.topic}")
    private String topic;

    private final ReactiveKafkaProducerTemplate<String, TokenDTO> kafkaProducerTemplate;
    private final KafkaReceiver<String, UserInfoDTO> kafkaReceiver;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        log.info("요청이 들어온 경로: {}", path);

        // 예외 처리(로그인 및 회원가입)
        if (path.startsWith("/api/users/login") || path.startsWith("/api/users/signup")) {
            return chain.filter(exchange);
        }

        // 토큰 쿠키에서 추출
        String token = extractTokenFromCookies(exchange.getRequest());
        if (token == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No access token found"));
        }

        UUID id = UUID.randomUUID();
        log.info("추출된 토큰: {} // 아이디: {}", token, id);

        TokenDTO tokenDTO = new TokenDTO(id, token);

        // 인증 요청 Kafka 전송
        return kafkaProducerTemplate.send(topic, tokenDTO)
                .then(Mono.defer(() -> {
                    // 인증 응답 대기
                    return kafkaReceiver
                            .receive()
                            /**
                             * 필터 쪽 여기가 문제인 거 같은데 왜 그런질 모르겠네... 흠... 다시 해 보자...
                             */
                            .filter(record -> record.key().equals(id.toString()))
                            .next()
                            .map(ConsumerRecord::value)
                            .flatMap(userInfoDTO -> {
                                // 인증 결과를 요청 헤더에 추가
                                ServerHttpRequest modifiedRequest = exchange.getRequest()
                                        .mutate()
                                        .header("User", userInfoDTO.toString())
                                        .build();

                                log.info("응답 이메일 및 권한: {}. {}", userInfoDTO.getEmail(), userInfoDTO.getRole());

                                // 수정된 요청으로 다음 단계로 넘기기
                                return chain.filter(exchange.mutate().request(modifiedRequest).build());
                            })
                            .timeout(Duration.ofSeconds(10))  // 타임아웃 설정
                            .onErrorResume(e -> {
                                // 오류 처리
                                return Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error during authentication", e));
                            });
                }));
    }

    private String extractTokenFromCookies(ServerHttpRequest request) {
        // 쿠키에서 엑세스 토큰 추출
        String token = null;

        if (request.getCookies().containsKey(COOKIE_AUTH_HEADER)) {
            HttpCookie accessTokenCookie = request.getCookies().getFirst(COOKIE_AUTH_HEADER);
            if (accessTokenCookie != null) {
                log.info("쿠키로부터 추출한 엑세스 토큰: {}", accessTokenCookie.getValue());
                token = accessTokenCookie.getValue();
            }
        }

        return token;
    }
}
