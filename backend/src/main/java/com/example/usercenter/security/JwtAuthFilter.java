package com.example.usercenter.security;

import com.example.usercenter.common.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 只负责把合法 access token 变成 SecurityContext。
 * 令牌缺失/非法时不做响应，交给 AuthenticationEntryPoint 统一输出 401，
 * 并把失败原因（过期 / 非法 / 会话已撤销）放进 request attribute 供入口点区分错误码。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTR = "uc.authError";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final SessionGuard sessionGuard;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            try {
                Claims claims = jwtService.parse(token);
                UserPrincipal principal = jwtService.toPrincipal(claims);
                if (sessionGuard.isActive(principal.getSessionId())) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    request.setAttribute(AUTH_ERROR_ATTR, ErrorCode.TOKEN_EXPIRED);
                    SecurityContextHolder.clearContext();
                }
            } catch (ExpiredJwtException ex) {
                request.setAttribute(AUTH_ERROR_ATTR, ErrorCode.TOKEN_EXPIRED);
                SecurityContextHolder.clearContext();
            } catch (JwtException | IllegalArgumentException ex) {
                request.setAttribute(AUTH_ERROR_ATTR, ErrorCode.UNAUTHORIZED);
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
