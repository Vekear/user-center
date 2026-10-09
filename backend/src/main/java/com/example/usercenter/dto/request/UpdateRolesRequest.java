package com.example.usercenter.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class UpdateRolesRequest {

    /** 短角色码：USER / ADMIN（服务端会归一化成 ROLE_ 前缀） */
    @NotEmpty(message = "至少需要一个角色")
    private List<@Pattern(regexp = "^(ROLE_)?(USER|ADMIN)$", message = "角色取值不合法") String> roles;
}
