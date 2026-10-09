package com.example.usercenter.storage;

import com.example.usercenter.common.BizException;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.config.UcProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * 图片校验：先看字节魔数，再用 ImageReader 只读头部拿到格式与像素尺寸。
 * 不整图解码，因此"图片解压炸弹"（小文件、超大像素）在分配内存之前就被拦下。
 * 支持 JPEG / PNG；WebP 需要额外 ImageIO 插件，本版不收。
 */
@Component
@RequiredArgsConstructor
public class ImageValidator {

    private static final Set<String> ALLOWED_FORMATS = Set.of("jpeg", "jpg", "png");

    private final UcProperties properties;

    public ImageInfo validate(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw BizException.of(ErrorCode.FILE_INVALID, "文件为空");
        }
        long maxBytes = properties.getAvatar().getMaxBytes();
        if (bytes.length > maxBytes) {
            throw BizException.of(ErrorCode.FILE_TOO_LARGE,
                    "文件不能超过 " + (maxBytes / 1024 / 1024) + "MB");
        }
        String format = detectFormat(bytes);
        if (format == null) {
            throw BizException.of(ErrorCode.FILE_INVALID, "只支持 JPEG 或 PNG 图片");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw BizException.of(ErrorCode.FILE_INVALID, "无法读取图片内容");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw BizException.of(ErrorCode.FILE_INVALID, "无法读取图片内容");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                checkDimension(width, height);
                String readerFormat = reader.getFormatName() == null
                        ? format
                        : reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!ALLOWED_FORMATS.contains(readerFormat)) {
                    throw BizException.of(ErrorCode.FILE_INVALID, "只支持 JPEG 或 PNG 图片");
                }
                return new ImageInfo("png".equals(readerFormat) ? "png" : "jpg",
                        "png".equals(readerFormat) ? "image/png" : "image/jpeg",
                        width, height);
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            throw BizException.of(ErrorCode.FILE_INVALID, "图片解析失败");
        }
    }

    private void checkDimension(int width, int height) {
        int max = properties.getAvatar().getMaxDimension();
        int min = properties.getAvatar().getMinDimension();
        if (width < min || height < min) {
            throw BizException.of(ErrorCode.FILE_INVALID, "图片尺寸过小，至少 " + min + "×" + min);
        }
        if (width > max || height > max) {
            throw BizException.of(ErrorCode.FILE_INVALID, "图片尺寸过大，最多 " + max + "×" + max);
        }
    }

    private String detectFormat(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return "png";
        }
        return null;
    }

    public record ImageInfo(String extension, String mimeType, int width, int height) {
    }
}
