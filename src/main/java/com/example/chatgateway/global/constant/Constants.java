package com.example.chatgateway.global.constant;

public final class Constants {
    private Constants() {
    }

    public static final String CHAT_DESTINATION = "/sub/chat/";
    public static final String REDIS_CHAT_PREFIX = "chat_";
    public static final String COOKIE_AUTH_HEADER = "Authorization";
    public static final String REDIS_REFRESH_KEY = "REFRESH_TOKEN:";
    public static final String REDIS_ACCESS_KEY = "ACCESS_TOKEN:";
    public static final String REDIS_SUBSCRIBE_KEY = "SUBSCRIBE:";

    // kafka 상수
    public static final String KAFKA_USER_TO_CHAT_TOPIC = "email";  // chat 인스턴스에 전파하기 위한 토픽
    public static final String KAFKA_OTHER_TO_USER_TOPIC = "authorization"; // user 인스턴스로 오는 날 것의 엑세스 토큰 수신을 위한 토픽
}

