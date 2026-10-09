package com.example.usercenter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.usercenter.entity.UserSession;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface UserSessionMapper extends BaseMapper<UserSession> {

    @Update("UPDATE user_session SET revoked = 1 WHERE user_id = #{userId} AND revoked = 0 AND id <> #{keepId}")
    int revokeOthers(Long userId, Long keepId);

    @Update("UPDATE user_session SET revoked = 1 WHERE user_id = #{userId} AND revoked = 0")
    int revokeAll(Long userId);

    @Update("UPDATE user_session SET revoked = 1 WHERE revoked = 0 AND expires_at < #{now}")
    int revokeExpired(LocalDateTime now);
}
