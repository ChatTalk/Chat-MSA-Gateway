package com.example.chatgateway.entity;

import lombok.Getter;

@Getter
public enum UserRoleEnum {
    ADMIN(Role.ADMIN),
    USER(Role.USER);

    private final String role;

    UserRoleEnum(String role) {
        this.role = role;
    }

    public static class Role {
        // 권한 이름 규칙은 "ROLE_"로 시작해야 함
        public static final String ADMIN = "ROLE_ADMIN";
        public static final String USER = "ROLE_USER";
    }
}
