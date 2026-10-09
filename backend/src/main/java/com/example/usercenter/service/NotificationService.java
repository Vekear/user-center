package com.example.usercenter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.usercenter.common.BizException;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.dto.request.NotificationQuery;
import com.example.usercenter.dto.request.SendNotificationRequest;
import com.example.usercenter.dto.response.NotificationResponse;
import com.example.usercenter.entity.Notification;
import com.example.usercenter.entity.User;
import com.example.usercenter.mapper.NotificationMapper;
import com.example.usercenter.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内通知：个人收件箱 + 管理员群发。
 * 未读数走 COUNT 查询而不是内存过滤，列表分页由数据库保证。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** 群发上限：一次最多给这么多活跃用户发通知，防止误操作打爆数据库 */
    private static final int BROADCAST_LIMIT = 5000;

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;

    public void create(Long userId, String title, String content, String type) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(type);
        notificationMapper.insert(notification);
    }

    public void createWelcome(User user) {
        create(user.getId(), "欢迎加入用户中心",
                "你好 " + user.getNickname() + "，你的账号已创建完成。建议先补全个人资料并设置一个强密码。",
                Notification.TYPE_ACCOUNT);
    }

    public PageResult<NotificationResponse> page(Long userId, NotificationQuery query) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreatedAt);
        if (Boolean.TRUE.equals(query.getUnreadOnly())) {
            wrapper.isNull(Notification::getReadAt);
        }
        Page<Notification> page = notificationMapper.selectPage(
                new Page<>(query.normalizedPage(), query.normalizedSize()), wrapper);
        List<NotificationResponse> list = page.getRecords().stream().map(NotificationResponse::from).toList();
        return PageResult.of(list, page.getTotal(), query.normalizedPage(), query.normalizedSize());
    }

    public long unreadCount(Long userId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .isNull(Notification::getReadAt));
    }

    public long unreadCountAllUsers() {
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .isNull(Notification::getReadAt));
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null || !notification.getUserId().equals(userId)) {
            throw BizException.of(ErrorCode.NOT_FOUND, "通知不存在");
        }
        if (notification.getReadAt() != null) {
            return;
        }
        Notification update = new Notification();
        update.setId(notificationId);
        update.setReadAt(LocalDateTime.now());
        notificationMapper.updateById(update);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notificationMapper.markAllRead(userId, LocalDateTime.now());
    }

    /**
     * 管理员群发。all=true 发给所有 ACTIVE 用户，否则发给指定 userIds。
     *
     * @return 实际写入的条数
     */
    @Transactional
    public int send(SendNotificationRequest request) {
        List<Long> recipients;
        if (Boolean.TRUE.equals(request.getAll())) {
            recipients = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .select(User::getId)
                            .eq(User::getStatus, User.STATUS_ACTIVE)
                            .last("LIMIT " + BROADCAST_LIMIT))
                    .stream().map(User::getId).toList();
        } else {
            recipients = request.getUserIds() == null ? List.of() : request.getUserIds().stream().distinct().toList();
        }
        if (recipients.isEmpty()) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "没有可发送的收件人");
        }
        for (Long userId : recipients) {
            create(userId, request.getTitle(), request.getContent(), Notification.TYPE_SYSTEM);
        }
        log.info("群发通知完成 recipients={} title={}", recipients.size(), request.getTitle());
        return recipients.size();
    }
}
