package com.example.usercenter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 管理员重置密码后只返回这一次的临时密码，不落库明文。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TempPasswordResponse {

    private String tempPassword;
}
