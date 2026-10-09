package com.example.usercenter.storage;

import com.example.usercenter.common.BizException;
import com.example.usercenter.config.UcProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageValidatorTest {

    private UcProperties properties;
    private ImageValidator validator;

    @BeforeEach
    void setUp() {
        properties = new UcProperties();
        properties.getAvatar().setMaxBytes(200 * 1024);
        properties.getAvatar().setMaxDimension(512);
        properties.getAvatar().setMinDimension(16);
        validator = new ImageValidator(properties);
    }

    private byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.decode("#2F5D62"));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void acceptsValidPng() throws IOException {
        ImageValidator.ImageInfo info = validator.validate(png(64, 64));

        assertThat(info.extension()).isEqualTo("png");
        assertThat(info.mimeType()).isEqualTo("image/png");
        assertThat(info.width()).isEqualTo(64);
    }

    @Test
    void rejectsNonImageBytes() {
        assertThatThrownBy(() -> validator.validate("not an image at all".getBytes()))
                .isInstanceOf(BizException.class);
    }

    @Test
    void rejectsEmptyBytes() {
        assertThatThrownBy(() -> validator.validate(new byte[0])).isInstanceOf(BizException.class);
    }

    @Test
    void rejectsTooSmallImage() throws IOException {
        assertThatThrownBy(() -> validator.validate(png(8, 8))).isInstanceOf(BizException.class);
    }

    @Test
    void rejectsOversizedDimensionsBeforeDecoding() throws IOException {
        byte[] bytes = png(1200, 1200);

        assertThatThrownBy(() -> validator.validate(bytes))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("过大");
    }

    @Test
    void rejectsFileLargerThanLimit() throws IOException {
        properties.getAvatar().setMaxBytes(100);
        byte[] bytes = png(64, 64);

        assertThatThrownBy(() -> validator.validate(bytes))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能超过");
    }
}
