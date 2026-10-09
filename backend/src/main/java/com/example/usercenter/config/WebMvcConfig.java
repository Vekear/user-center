package com.example.usercenter.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 头像静态资源映射。
 * 生成的头像文件名一律是 UUID，且只从配置的目录读取，客户端文件名不参与路径拼接。
 * CORS 只在 SecurityConfig 里配置一处：两处都配会让响应出现重复的 Access-Control-Allow-Origin。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final UcProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path avatarDir = Paths.get(properties.getAvatar().getDir()).toAbsolutePath().normalize();
        registry.addResourceHandler(properties.getAvatar().getPublicPrefix() + "/**")
                .addResourceLocations(avatarDir.toUri().toString())
                .setCachePeriod(3600);
    }
}
