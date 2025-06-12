package com.messenger.chat.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisPublisher {

    private final StringRedisTemplate redisTemplate;

    public void publish(String channel, String message) {
        log.debug("📤 Publicando no canal {}: {}", channel, message);
        redisTemplate.convertAndSend(channel, message);
    }
}