package com.messenger.chat.redis;

import com.messenger.chat.websocket.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.Executor;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSubscriber implements MessageListener {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final Executor taskExecutor;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String canal = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);

        if (!canal.startsWith("chat:")) {
            log.warn("📛 Canal inválido ignorado: {}", canal);
            return;
        }

        log.info("📩 Redis: nova mensagem no canal {}: {}", canal, body);

        String destinatarioEmail = canal.substring("chat:".length());

        Set<WebSocketSession> sessions = chatWebSocketHandler.getSessions(destinatarioEmail);
        if (sessions.isEmpty()) {
            log.warn("⚠️ Nenhuma sessão WebSocket ativa para {}", destinatarioEmail);
            return;
        }

        // Criar virtual thread para envio async
        taskExecutor.execute(() -> {
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage(body));
                        log.info("📤 Mensagem enviada via WebSocket para {} na sessão {}", destinatarioEmail, session.getId());
                    } catch (Exception e) {
                        log.error("Erro ao enviar mensagem via WebSocket para {} na sessão {}", destinatarioEmail, session.getId(), e);
                    }
                } else {
                    log.warn("⚠️ Sessão WebSocket {} para {} está fechada", session.getId(), destinatarioEmail);
                }
            }
        });
    }
}
