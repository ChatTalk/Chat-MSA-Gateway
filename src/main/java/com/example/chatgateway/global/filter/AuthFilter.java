package com.example.chatgateway.global.filter;

import com.example.chatgateway.domain.dto.TokenDTO;
import com.example.chatgateway.domain.dto.UserInfoDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

import static com.example.chatgateway.global.constant.Constants.COOKIE_AUTH_HEADER;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFilter implements GatewayFilter {

    @Value("${kafka.topic}")
    private String topic;

    private final ReactiveKafkaProducerTemplate<String, TokenDTO> kafkaProducerTemplate;
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

        String id = createFingerPrint(exchange, token);
        log.info("추출된 토큰: {} // 아이디: {}", token, id);

        TokenDTO tokenDTO = new TokenDTO(id, token);

        // 인증 요청 Kafka 전송(파티션의 존재 이유: 묶어야 할 메세지들을 파티션으로 보내면서 대기열 구현)
        return kafkaProducerTemplate.send(topic, tokenDTO)
                .flatMap(result -> {
                    // Kafka에 메시지 전송 후 Redis에서 결과를 비동기적으로 조회
                    return checkRedisForUserInfo(id)
                            .flatMap(userInfoDTO -> {
                                ServerHttpRequest modifiedRequest = exchange.getRequest()
                                        .mutate()
                                        .header("email", userInfoDTO.getEmail())
                                        .header("role", userInfoDTO.getRole())
                                        .build();

                                updateTokenCookieIfNeeded(exchange, token, userInfoDTO.getToken());
                                return chain.filter(exchange.mutate().request(modifiedRequest).build());
                            });
                })
                .onErrorResume(e -> {
                    // Kafka 전송 오류 처리
                    log.error("Kafka 전송 오류: {}", e.getMessage());
                    return Mono.error(
                            new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "카프카 전송 로직 에러 발생"));
                });
    }

    // 쿠키 추출 메소드
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
        if (!currentToken.equals(newToken)) {
            log.info("토큰 업데이트: 현재 토큰 {} ->, 새로운 토큰 {}", currentToken, newToken);
            exchange.getResponse().addCookie(ResponseCookie.from(COOKIE_AUTH_HEADER, newToken)
                    .path("/")  // 쿠키의 유효 경로 설정
//                    .httpOnly(true)  // 보안 설정 (HTTP만 접근 가능)
                    .maxAge(Duration.ofHours(1))  // 쿠키 유효기간 설정
                    .build());
        }
    }

    // redis 캐시 조회 메소드
    private Mono<UserInfoDTO> checkRedisForUserInfo(String id) {
        return Mono.defer(() -> {
                    UserInfoDTO userInfo = userInfoTemplate.opsForValue().get(id);

                    if (userInfo != null) {
                        log.info("Redis에서 사용자 정보 조회 성공 - UserInfo: {}", userInfo);  // 성공 시 로그 추가
                        return Mono.just(userInfo);
                    } else {
                        log.info("Redis에서 사용자 정보가 없음 - ID: {}\n 조회하는 시간: {}", id, System.nanoTime());  // 데이터가 없는 경우 로그 추가
                        return Mono.empty();
                    }
                })
                .repeatWhenEmpty(flux -> flux
                        .delayElements(Duration.ofMillis(50)) // 0.05초 간격으로 재시도
                        .take(5)) // 재시도
                .timeout(Duration.ofSeconds(10)) // 타임아웃 설정
                .onErrorResume(e ->
                        Mono.error(new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR, "레디스 유저 임시정보 조회 에러", e)));
    }

    // 디바이스 핑거프린트 생성 메소드
    private String createFingerPrint(ServerWebExchange exchange, String token) {
        ServerHttpRequest request = exchange.getRequest();

        String userAgent = request.getHeaders().getFirst(HttpHeaders.USER_AGENT);
        String ip = request.getRemoteAddress() != null ? request.getRemoteAddress().getAddress().getHostAddress() : "unknown-ip";
        String data = userAgent + ip + token;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));

            // 바이트 배열 16진수 문자열로 변환
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("해시 알고리즘 탐색 불가", e);
        }
    }
}
