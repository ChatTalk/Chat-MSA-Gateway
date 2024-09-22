package com.example.chatgateway.global.filter;

import com.example.chatgateway.domain.dto.TokenDTO;
import com.example.chatgateway.domain.dto.UserInfoDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;

import java.time.Duration;
import java.util.Objects;
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
    private final RedisTemplate<String, UserInfoDTO> userInfoTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String uri = exchange.getRequest().getURI().toString();
        log.info("요청이 들어온 경로: {}", uri);
        String path = exchange.getRequest().getURI().getPath();

        log.info("응답 초기 헤더 확인: {}", exchange.getResponse().getHeaders()); // 여기서는 문제 없음

        // 예외 처리(로그인 및 회원가입)
        if (path.startsWith("/api/users/login") || path.startsWith("/api/users/signup")) {
            return chain.filter(exchange);
        }

        String token = extractTokenFromCookies(exchange.getRequest());
        if (token == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No access token found"));
        }

        UUID id = UUID.randomUUID();
        log.info("추출된 토큰: {} // 아이디: {}", token, id);

        TokenDTO tokenDTO = new TokenDTO(id, token);

        // 인증 요청 Kafka 전송
        // 동일한 파티션을 왕복
        return kafkaProducerTemplate.send(topic, id.toString(), tokenDTO)
                .then(Mono.defer(() -> {
                    // 인증 응답 대기
                    return kafkaReceiver
                            .receive()
                            .filter(record -> record.key().equals(id.toString()))
                            .next()
                            .timeout(Duration.ofSeconds(10))  // 타임아웃 설정
                            .onErrorResume(e -> {
                                        // 오류 처리
                                        return Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error during authentication", e));
                                    })
                            .map(ConsumerRecord::value)
                            .flatMap(userInfoDTO -> {
                                // 쿠키 업데이트
                                updateTokenCookieIfNeeded(exchange, token, userInfoDTO.getToken());

                                // 인증 결과를 요청 헤더에 추가
                                ServerHttpRequest modifiedRequest = exchange.getRequest()
                                        .mutate()
                                        .header("email", userInfoDTO.getEmail())
                                        .header("role", userInfoDTO.getRole())
                                        .build();

                                log.info("응답 이메일 및 권한: {}. {}", userInfoDTO.getEmail(), userInfoDTO.getRole());
                                log.info("인증 이후의 응답 헤더 확인: {}", exchange.getResponse().getHeaders());

                                // 수정된 요청으로 다음 단계로 넘기기
                                return chain.filter(exchange.mutate().request(modifiedRequest).build());
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

    // 쿠키 업데이트 메소드
    private void updateTokenCookieIfNeeded(ServerWebExchange exchange, String currentToken, String newToken) {
        log.info("토큰 업데이트? 현재 토큰 {}, 새로운 토큰 {}", currentToken, newToken);
        log.info("토큰이 같은지 다른지: {}", currentToken.equals(newToken));
        if (!currentToken.equals(newToken)) {
            exchange.getResponse().addCookie(ResponseCookie.from(COOKIE_AUTH_HEADER, newToken)
                    .path("/")  // 쿠키의 유효 경로 설정
//                    .httpOnly(true)  // 보안 설정 (HTTP만 접근 가능)
                    .maxAge(Duration.ofHours(1))  // 쿠키 유효기간 설정
                    .build());
        }
    }
}
