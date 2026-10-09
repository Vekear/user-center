package com.example.usercenter.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StatsResponse {

    private long userTotal;
    private long userActive;
    private long userDisabled;
    private long notificationUnread;
    private long auditToday;
    private long newUsers7d;
}
