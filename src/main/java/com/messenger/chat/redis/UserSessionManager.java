package com.messenger.chat.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserSessionManager {

    private static final Duration ONLINE_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    /**
     * Incrementa contador de sessões do usuário, marcando-o como online.
     */
    public void userConnected(String email) {
        String key = redisKey(email);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count > 0) {
            redisTemplate.expire(key, ONLINE_TTL);
        }
        log.info("Usuário {} conectado. Sessões ativas: {}", email, count);
    }

    /**
     * Decrementa contador de sessões do usuário. Remove a chave se chegar a zero ou menor.
     */
    public void userDisconnected(String email) {
        String key = redisKey(email);
        Long count = redisTemplate.opsForValue().decrement(key);
        if (count == null || count <= 0) {
            redisTemplate.delete(key);
            log.info("Usuário {} desconectado. Nenhuma sessão ativa.", email);
        } else {
            redisTemplate.expire(key, ONLINE_TTL); // renova TTL enquanto houver sessões
            log.info("Usuário {} desconectado. Sessões ativas restantes: {}", email, count);
        }
    }

    /**
     * Verifica se usuário está online (tem sessões ativas).
     */
    public boolean isUserOnline(String email) {
        String key = redisKey(email);
        String value = redisTemplate.opsForValue().get(key);
        return value != null && !"0".equals(value);
    }

    private String redisKey(String email) {
        return "online:" + email;
    }
}
