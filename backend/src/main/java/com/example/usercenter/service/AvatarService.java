package com.example.usercenter.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.usercenter.audit.AuditRecorder;
import com.example.usercenter.common.BizException;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.dto.response.AvatarResponse;
import com.example.usercenter.entity.User;
import com.example.usercenter.mapper.UserMapper;
import com.example.usercenter.security.SecurityUtils;
import com.example.usercenter.storage.AvatarStorage;
import com.example.usercenter.storage.ImageValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 头像：校验（魔数 + 像素上限）→ 落盘（UUID 文件名）→ 更新用户 → 尽力删除旧文件。
 * 前端把裁剪后的 512×512 JPEG 传上来，服务端不再做二次图像处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarService {

    private final UserMapper userMapper;
    private final ImageValidator imageValidator;
    private final AvatarStorage avatarStorage;
    private final AuditRecorder auditRecorder;

    @Transactional
    public AvatarResponse upload(MultipartFile file) {
        Long userId = SecurityUtils.requireUserId();
        if (file == null || file.isEmpty()) {
            throw BizException.of(ErrorCode.FILE_INVALID, "请选择要上传的图片");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw BizException.of(ErrorCode.FILE_INVALID, "读取上传文件失败");
        }

        ImageValidator.ImageInfo info = imageValidator.validate(bytes);
        String publicUrl = avatarStorage.store(userId, bytes, info.extension());

        User current = userMapper.selectById(userId);
        String previousUrl = current == null ? null : current.getAvatarUrl();
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getAvatarUrl, publicUrl));
        // 数据库已指向新文件，旧文件清理失败不影响本次请求
        avatarStorage.deleteQuietly(previousUrl);

        auditRecorder.record(userId, current == null ? null : current.getUsername(), "AVATAR_UPLOAD", "USER",
                String.valueOf(userId), true, info.width() + "x" + info.height() + " " + info.extension());
        return new AvatarResponse(publicUrl);
    }

    @Transactional
    public void remove() {
        Long userId = SecurityUtils.requireUserId();
        User current = userMapper.selectById(userId);
        String previousUrl = current == null ? null : current.getAvatarUrl();
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getAvatarUrl, null));
        avatarStorage.deleteQuietly(previousUrl);
        auditRecorder.record(userId, current == null ? null : current.getUsername(), "AVATAR_DELETE", "USER",
                String.valueOf(userId), true, null);
    }
}
