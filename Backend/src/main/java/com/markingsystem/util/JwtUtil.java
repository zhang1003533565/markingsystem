package com.markingsystem.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret:}")
    private String secret;

    @Value("${jwt.expiration-hours:8}")
    private int expirationHours;

    @PostConstruct
    private void validateSecret() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret 未配置：请设置 JWT_SECRET 环境变量（或在本机 application-local.yml 中提供）");
        }
    }

    /**
     * 生成 JWT Token
     *
     * @param userId 用户ID
     * @param role   角色名称
     * @return JWT 字符串
     */
    public String generateToken(Long userId, String role) {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + (long) expirationHours * 3600 * 1000);

        return JWT.create()
                .withIssuer("marking-system")
                .withSubject(String.valueOf(userId))
                .withClaim("role", role)
                .withIssuedAt(now)
                .withExpiresAt(expiresAt)
                .sign(algorithm);
    }

    /**
     * 验证并解析 JWT Token
     *
     * @param token JWT 字符串
     * @return 解码后的 JWT，验证失败返回 null
     */
    public DecodedJWT validateToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            JWTVerifier verifier = JWT.require(algorithm)
                    .withIssuer("marking-system")
                    .build();
            return verifier.verify(token);
        } catch (JWTVerificationException exception) {
            return null;
        }
    }

    /**
     * 从 Token 中提取用户ID
     */
    public Long getUserId(DecodedJWT jwt) {
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从 Token 中提取角色
     */
    public String getRole(DecodedJWT jwt) {
        return jwt.getClaim("role").asString();
    }
}
