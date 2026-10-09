package com.example.usercenter.controller;

import com.example.usercenter.common.R;
import com.example.usercenter.dto.request.ChangePasswordRequest;
import com.example.usercenter.dto.request.UpdateProfileRequest;
import com.example.usercenter.dto.response.AvatarResponse;
import com.example.usercenter.dto.response.UserResponse;
import com.example.usercenter.service.AvatarService;
import com.example.usercenter.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "个人资料")
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final AvatarService avatarService;

    @Operation(summary = "读取当前用户资料")
    @GetMapping
    public R<UserResponse> current() {
        return R.ok(profileService.currentProfile());
    }

    @Operation(summary = "更新资料：昵称、邮箱、手机号、性别、生日、简介（传空字符串表示清空）")
    @PutMapping
    public R<UserResponse> update(@Valid @RequestBody UpdateProfileRequest request) {
        return R.ok(profileService.updateProfile(request));
    }

    @Operation(summary = "修改密码：成功后其他设备会话立即失效，当前设备保持登录")
    @PutMapping("/password")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        profileService.changePassword(request);
        return R.ok();
    }

    @Operation(summary = "上传头像（前端裁剪后的 JPEG/PNG，最大 2MB，尺寸上限 4096）")
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<AvatarResponse> uploadAvatar(@RequestPart("file") MultipartFile file) {
        return R.ok(avatarService.upload(file));
    }

    @Operation(summary = "删除头像")
    @DeleteMapping("/avatar")
    public R<Void> removeAvatar() {
        avatarService.remove();
        return R.ok();
    }
}
