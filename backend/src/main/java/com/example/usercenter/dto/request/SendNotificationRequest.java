package com.example.usercenter.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SendNotificationRequest {

    /** 指定收件人；与 all=true 二选一 */
    private List<Long> userIds;

    /** 为 true 时发给所有 ACTIVE 用户 */
    private Boolean all;

    @NotBlank(message = "标题不能为空")
    @Size(max = 128, message = "标题最多 128 个字符")
    private String title;

    @NotBlank(message = "内容不能为空")
    @Size(max = 1000, message = "内容最多 1000 个字符")
    private String content;
}
