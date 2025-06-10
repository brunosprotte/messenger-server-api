package com.messenger.chat.websocket;

import com.messenger.chat.JsonUtils;
import com.messenger.chat.infra.auth.TokenUtils;
import com.messenger.chat.infra.validator.ContatoValidator;
import com.messenger.chat.model.ChatMessage;
import com.messenger.chat.redis.RedisPublisher;
import com.messenger.chat.services.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final RedisPublisher redisPublisher;
    private final TokenUtils tokenUtils;
    private final ContatoValidator contatoValidator;
    private final MessageService messageService;

    // Mapa com email do usuário => sessão WebSocket
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = extrairToken(session);
        String email = tokenUtils.getEmailFromToken(token);

        if (email == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Token ausente ou inválido"));
            return;
        }

        sessions.put(email, session);
        log.info("🔗 Conexão aberta para: {}", email);
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.info("➡️ Mensagem recebida: {}", message.getPayload());

        String token = extrairToken(session);
        String remetenteEmail = tokenUtils.getEmailFromToken(token);
        if (remetenteEmail == null) {
            session.sendMessage(new TextMessage("Token inválido."));
            return;
        }

        String payload = message.getPayload();
        String destinatarioEmail = JsonUtils.extractField("to", payload);
        if (!contatoValidator.podeConversar(remetenteEmail, destinatarioEmail)) {
            log.warn("⛔ {} não pode conversar com {}", remetenteEmail, destinatarioEmail);
            session.sendMessage(new TextMessage("Você não tem permissão para conversar com este contato."));
            return;
        }

        String canal = "chat:" + destinatarioEmail;

        if (sessions.containsKey(destinatarioEmail)) {
            redisPublisher.publish(canal, payload);
            log.info("📤 Publicando no canal '{}'", canal);
        } else {
            ChatMessage mensagem = (ChatMessage) JsonUtils.toObject(payload, ChatMessage.class);
            messageService.salvarMensagemOffline(mensagem);
            log.info("💾 Destinatário offline, mensagem salva no DynamoDB.");
        }
    }

    private String extrairToken(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri == null || uri.getQuery() == null) return null;

            return Arrays.stream(uri.getQuery().split("&"))
                    .filter(q -> q.startsWith("token="))
                    .map(q -> q.substring("token=".length()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.error("Erro ao extrair token da URL", e);
            return null;
        }
    }
}
