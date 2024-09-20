package com.example.chatgateway.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserInfoDTO {
    private UUID id;
    private String email;
    private String role;
    private String token;
}
