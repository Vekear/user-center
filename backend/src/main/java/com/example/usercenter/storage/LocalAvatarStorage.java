package com.example.usercenter.storage;

import com.example.usercenter.common.BizException;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.config.UcProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * 本机磁盘实现。文件名一律由服务端生成（UUID + 受控扩展名），
 * 客户端提供的文件名永不参与路径拼接，因此不存在目录穿越。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalAvatarStorage implements AvatarStorage {

    private final UcProperties properties;

    @Override
    public String store(Long userId, byte[] bytes, String extension) {
        String safeExtension = "png".equalsIgnoreCase(extension) ? "png" : "jpg";
        Path dir = rootDir().resolve(String.valueOf(userId));
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + safeExtension;
        Path target = dir.resolve(fileName).normalize();
        if (!target.startsWith(rootDir())) {
            throw BizException.of(ErrorCode.FILE_INVALID);
        }
        try {
            Files.createDirectories(dir);
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException ex) {
            log.error("写入头像失败 userId={}", userId, ex);
            throw BizException.of(ErrorCode.SERVER_ERROR, "头像保存失败，请稍后再试");
        }
        return properties.getAvatar().getPublicPrefix() + "/" + userId + "/" + fileName;
    }

    @Override
    public void deleteQuietly(String publicUrl) {
        if (publicUrl == null || !publicUrl.startsWith(properties.getAvatar().getPublicPrefix() + "/")) {
            return;
        }
        String relative = publicUrl.substring(properties.getAvatar().getPublicPrefix().length() + 1);
        Path target = rootDir().resolve(relative).normalize();
        if (!target.startsWith(rootDir())) {
            log.warn("拒绝删除越界路径: {}", publicUrl);
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            log.warn("删除旧头像失败 path={}", target, ex);
        }
    }

    private Path rootDir() {
        return Paths.get(properties.getAvatar().getDir()).toAbsolutePath().normalize();
    }
}
