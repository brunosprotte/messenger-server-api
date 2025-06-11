package com.messenger.chat.redis;

import com.messenger.chat.websocket.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSubscriber implements MessageListener {

    private final ChatWebSocketHandler chatWebSocketHandler;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String canal = new String(message.getChannel());
        String body = new String(message.getBody());

        log.info("📩 Redis: nova mensagem no canal {}: {}", canal, body);

        // Extrai destinatário do canal (assumindo prefixo chat:email)
        String destinatarioEmail = canal.replace("chat:", "");

        WebSocketSession session = chatWebSocketHandler.getSession(destinatarioEmail);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(body));
                log.info("📤 Mensagem enviada via WebSocket para {}", destinatarioEmail);
            } catch (Exception e) {
                log.error("Erro ao enviar mensagem via WebSocket para {}: {}", destinatarioEmail, e.getMessage());
            }
        } else {
            log.warn("⚠️ Sessão WebSocket para {} não encontrada ou fechada", destinatarioEmail);
        }
    }
}