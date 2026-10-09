package com.example.usercenter.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteAccountRequest {

    @NotBlank(message = "请输入密码以确认注销")
    private String password;
}
