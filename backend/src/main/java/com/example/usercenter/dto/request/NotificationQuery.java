package com.example.usercenter.dto.request;

import com.example.usercenter.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NotificationQuery extends PageQuery {

    private Boolean unreadOnly;
}
