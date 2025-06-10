package com.messenger.chat.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

@Slf4j
@Component
public class RedisSubscriber implements MessageListener {

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(pattern);
        String body = new String(message.getBody());
        log.info("📩 Received message on channel {}: {}", channel, body);
    }
}