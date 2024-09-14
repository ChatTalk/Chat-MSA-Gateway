package com.example.chatgateway.dto;

import com.example.chatgateway.entity.UserRoleEnum;
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
