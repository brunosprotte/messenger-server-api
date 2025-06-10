package com.messenger.chat.infra.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.stereotype.Component;

@Component
public class TokenUtils {

    private static final String SECRET = "7ce86ced-b98f-4ff0-8366-f27b0ffcdc48";

    public String getEmailFromToken(String token) {
        try {
            return JWT.require(Algorithm.HMAC256(SECRET))
                    .withIssuer("local-auth0")
                    .build()
                    .verify(token)
                    .getClaim("email")
                    .asString();
        } catch (Exception e) {
            return null;
        }
    }
}
