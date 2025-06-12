package com.messenger.chat.config;

import com.messenger.chat.redis.RedisSubscriber;
import com.messenger.chat.websocket.ChatWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

import java.util.concurrent.Executor;

@Configuration
public class RedisConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        // Configuração simples local, ajuste se usar Redis remoto ou autenticação
        return new LettuceConnectionFactory("localhost", 6379);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter listenerAdapter,
            Executor taskExecutor) {   // injeta seu executor virtual aqui

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, new PatternTopic("chat:*"));
        container.setTaskExecutor(taskExecutor);  // usa executor virtual para processar as mensagens
        return container;
    }

    @Bean
    public MessageListenerAdapter listenerAdapter(RedisSubscriber subscriber) {
        // O método 'onMessage' será chamado para as mensagens recebidas
        return new MessageListenerAdapter(subscriber, "onMessage");
    }

    @Bean
    public RedisSubscriber redisSubscriber(ChatWebSocketHandler chatWebSocketHandler, Executor taskExecutor) {
        return new RedisSubscriber(chatWebSocketHandler, taskExecutor);
    }
}
