package com.example.usercenter.dto.request;

import com.example.usercenter.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminUserQuery extends PageQuery {

    /** 用户名/邮箱/昵称模糊匹配 */
    private String keyword;

    /** ACTIVE / DISABLED */
    private String status;

    /** USER / ADMIN */
    private String role;
}
