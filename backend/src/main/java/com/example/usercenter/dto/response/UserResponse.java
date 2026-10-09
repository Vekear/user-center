package com.example.usercenter.dto.response;

import com.example.usercenter.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 对外暴露的用户视图。roles 使用短码（USER/ADMIN），前端判断权限更直观。
 */
@Data
@Builder
public class UserResponse {

    private Long id;
    private String username;
    private String email;
    private String phone;
    private String nickname;
    private String avatarUrl;
    private String gender;
    private LocalDate birthday;
    private String bio;
    private String status;
    private List<String> roles;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;

    public static UserResponse from(User user, List<String> roleCodes) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .gender(user.getGender())
                .birthday(user.getBirthday())
                .bio(user.getBio())
                .status(user.getStatus())
                .roles(shortCodes(roleCodes))
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    public static List<String> shortCodes(List<String> roleCodes) {
        if (roleCodes == null) {
            return List.of();
        }
        return roleCodes.stream()
                .map(code -> code.startsWith("ROLE_") ? code.substring("ROLE_".length()) : code)
                .toList();
    }
}
