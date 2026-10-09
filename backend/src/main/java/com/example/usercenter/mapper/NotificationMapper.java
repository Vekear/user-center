package com.example.usercenter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.usercenter.entity.Notification;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface NotificationMapper extends BaseMapper<Notification> {

    @Update("UPDATE notification SET read_at = #{now} WHERE user_id = #{userId} AND read_at IS NULL")
    int markAllRead(Long userId, LocalDateTime now);
}
