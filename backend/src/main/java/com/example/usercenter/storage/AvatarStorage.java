package com.example.usercenter.storage;

/**
 * 头像存储抽象。当前实现写本机磁盘；换成对象存储时只需替换实现，
 * 调用方拿到的仍然是可以直接给前端用的公开 URL 前缀 + 相对路径。
 */
public interface AvatarStorage {

    /**
     * @param userId    归属用户，用于分目录，避免单目录文件过多
     * @param bytes     图片字节
     * @param extension 由 ImageValidator 判定的扩展名（jpg/png）
     * @return 可直接访问的公开路径，例如 /api/files/avatars/12/xxxx.jpg
     */
    String store(Long userId, byte[] bytes, String extension);

    /**
     * 删除旧头像。失败只记日志，不向上抛：换头像不该因为清理失败而整体失败。
     */
    void deleteQuietly(String publicUrl);
}
