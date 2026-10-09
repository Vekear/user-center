package com.example.usercenter.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 认证主体。携带 sessionId，使得"退出其他设备"能立刻生效：
 * 访问令牌虽然无状态，但每个请求都会校验它所属的会话是否还活着。
 */
@Getter
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String status;
    private final List<String> roleCodes;
    private final Long sessionId;
    private final boolean enabled;

    public UserPrincipal(Long id, String username, String status, List<String> roleCodes, Long sessionId) {
        this.id = id;
        this.username = username;
        this.status = status;
        this.roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
        this.sessionId = sessionId;
        this.enabled = "ACTIVE".equals(status);
    }

    public static UserPrincipal of(Long id, String username, String status, List<String> roleCodes, Long sessionId) {
        return new UserPrincipal(id, username, status, roleCodes, sessionId);
    }

    public boolean isAdmin() {
        return roleCodes.contains("ROLE_ADMIN");
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roleCodes.stream().map(SimpleGrantedAuthority::new).toList();
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
