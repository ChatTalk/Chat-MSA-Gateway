package com.example.chatgateway.domain.dto;

import com.example.chatgateway.domain.entity.UserRoleEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class UserInfoDTO {
    private String email;
    private UserRoleEnum role;
}
