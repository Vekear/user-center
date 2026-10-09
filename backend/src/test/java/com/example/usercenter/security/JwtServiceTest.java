package com.example.usercenter.security;

import com.example.usercenter.config.UcProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private UcProperties properties(String secret, int minutes) {
        UcProperties properties = new UcProperties();
        properties.getJwt().setSecret(secret);
        properties.getJwt().setAccessTokenMinutes(minutes);
        properties.getJwt().setIssuer("user-center-test");
        return properties;
    }

    @Test
    void issueAndParseRoundTrip() {
        JwtService service = new JwtService(properties("unit-test-secret-0123456789abcdefghijkl", 30));
        UserPrincipal principal = new UserPrincipal(7L, "alice", "ACTIVE", List.of("ROLE_USER"), 42L);

        String token = service.createAccessToken(principal);
        Claims claims = service.parse(token);
        UserPrincipal parsed = service.toPrincipal(claims);

        assertThat(parsed.getId()).isEqualTo(7L);
        assertThat(parsed.getUsername()).isEqualTo("alice");
        assertThat(parsed.getRoleCodes()).containsExactly("ROLE_USER");
        assertThat(parsed.getSessionId()).isEqualTo(42L);
        assertThat(service.accessTokenSeconds()).isEqualTo(30 * 60);
    }

    @Test
    void expiredTokenIsRejectedAsExpired() {
        JwtService service = new JwtService(properties("unit-test-secret-0123456789abcdefghijkl", -1));
        String token = service.createAccessToken(
                new UserPrincipal(1L, "bob", "ACTIVE", List.of("ROLE_USER"), 1L));

        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService issuer = new JwtService(properties("unit-test-secret-0123456789abcdefghijkl", 30));
        JwtService verifier = new JwtService(properties("another-secret-0123456789abcdefghijklmn", 30));
        String token = issuer.createAccessToken(
                new UserPrincipal(1L, "bob", "ACTIVE", List.of("ROLE_USER"), 1L));

        assertThatThrownBy(() -> verifier.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> new JwtService(properties("too-short", 30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("uc.jwt.secret");
    }

    @Test
    void refreshTokensAreRandomAndHashIsStable() {
        String first = Tokens.newRefreshToken();
        String second = Tokens.newRefreshToken();

        assertThat(first).isNotEqualTo(second);
        assertThat(first).hasSizeGreaterThanOrEqualTo(40);
        assertThat(Tokens.sha256Hex(first)).hasSize(64).isEqualTo(Tokens.sha256Hex(first));
        assertThat(Tokens.sha256Hex(first)).isNotEqualTo(Tokens.sha256Hex(second));
    }
}
