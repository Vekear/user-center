package com.example.usercenter.dto.request;

import com.example.usercenter.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AuditLogQuery extends PageQuery {

    private Long userId;
    private String action;
    private Boolean success;

    /** 起始时间（含），ISO-8601，例如 2026-10-09T00:00:00 */
    private String from;

    /** 结束时间（含） */
    private String to;
}
