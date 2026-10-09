package com.example.usercenter.security;

import com.example.usercenter.config.UcProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * access token 的签发与校验。refresh token 不在这里：它是随机串，哈希后落库。
 * 令牌里带 sid（会话 id），是"退出其他设备立即生效"的依据。
 */
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_SESSION = "sid";
    private static final String CLAIM_USERNAME = "username";

    private final SecretKey key;
    private final String issuer;

    @Getter
    private final Duration accessTokenTtl;

    public JwtService(UcProperties properties) {
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "uc.jwt.secret 未配置或长度不足 " + MIN_SECRET_BYTES + " 字节，请设置环境变量 UC_JWT_SECRET");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = properties.getJwt().getIssuer();
        this.accessTokenTtl = Duration.ofMinutes(properties.getJwt().getAccessTokenMinutes());
    }

    public long accessTokenSeconds() {
        return accessTokenTtl.toSeconds();
    }

    public String createAccessToken(UserPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(principal.getId()))
                .issuer(issuer)
                .claim(CLAIM_USERNAME, principal.getUsername())
                .claim(CLAIM_ROLES, principal.getRoleCodes())
                .claim(CLAIM_SESSION, principal.getSessionId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key)
                .compact();
    }

    /**
     * @throws io.jsonwebtoken.ExpiredJwtException 令牌过期
     * @throws JwtException                        其他非法令牌
     */
    public Claims parse(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @SuppressWarnings("unchecked")
    public UserPrincipal toPrincipal(Claims claims) {
        Long id = Long.valueOf(claims.getSubject());
        String username = claims.get(CLAIM_USERNAME, String.class);
        List<String> roles = claims.get(CLAIM_ROLES, List.class);
        Number sessionId = claims.get(CLAIM_SESSION, Number.class);
        return new UserPrincipal(id, username, "ACTIVE", roles == null ? List.of() : roles,
                sessionId == null ? null : sessionId.longValue());
    }
}
