package com.example.usercenter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 未读数：单独一个对象而不是裸数字，方便以后扩展（比如按类型分组的未读） */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnreadCountResponse {

    private long count;
}
