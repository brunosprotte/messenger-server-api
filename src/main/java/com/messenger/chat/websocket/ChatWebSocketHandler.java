package com.messenger.chat.websocket;

import com.messenger.chat.JsonUtils;
import com.messenger.chat.infra.auth.TokenUtils;
import com.messenger.chat.infra.validator.ContatoValidator;
import com.messenger.chat.model.ChatMessage;
import com.messenger.chat.redis.RedisPublisher;
import com.messenger.chat.redis.UserSessionManager;
import com.messenger.chat.services.ContatosService;
import com.messenger.chat.services.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final RedisPublisher redisPublisher;
    private final TokenUtils tokenUtils;
    private final ContatoValidator contatoValidator;
    private final MessageService messageService;
    private final UserSessionManager userSessionManager;
    private final ContatosService contatosService;
    private final Executor taskExecutor;

    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = extrairToken(session);
        String email = tokenUtils.getEmailFromToken(token);

        if (email == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Token ausente ou inválido"));
            return;
        }

        sessions.computeIfAbsent(email, k -> ConcurrentHashMap.newKeySet()).add(session);
        userSessionManager.userConnected(email);
        log.info("🔗 Conexão aberta para: {}", email);

        Map<String, String> payload = Map.of(
                "type", "status",
                "user", email,
                "status", "online"
        );

        contatosService.listarContatos(email).thenAcceptAsync(contatos -> {
            for (String contato : contatos) {
                if (userSessionManager.isUserOnline(contato)) {
                    redisPublisher.publish("chat:" + contato, JsonUtils.toJson(payload));
                    log.info("🔔 Notificação de presença enviada para {} sobre {}", contato, email);
                }
            }
        }, taskExecutor).exceptionally(ex -> {
            log.error("Erro ao notificar contatos sobre status online de {}", email, ex);
            return null;
        });

        // Buscar mensagens offline async e enviar para o cliente sem bloquear a thread
        messageService.buscarMensagensOffline(email).thenAcceptAsync(mensagensOffline -> {
            for (ChatMessage msg : mensagensOffline) {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(JsonUtils.toJson(msg)));
                        log.info("💬 Mensagem offline enviada para {}: {}", email, msg);
                    }
                } catch (Exception e) {
                    log.error("Erro ao enviar mensagem offline para {}", email, e);
                }
            }
            // Depois de enviar, remove as mensagens offline
            messageService.removerMensagensOffline(email);
        }, taskExecutor).exceptionally(ex -> {
            log.error("Erro ao buscar/enviar mensagens offline para {}", email, ex);
            return null;
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String token = extrairToken(session);
        String email = tokenUtils.getEmailFromToken(token);

        if (email != null) {
            removerSessao(session);

            contatosService.listarContatos(email).thenAcceptAsync(contatos -> {
                Map<String, String> payload = Map.of(
                        "type", "status",
                        "user", email,
                        "status", "offline"
                );

                for (String contato : contatos) {
                    if (userSessionManager.isUserOnline(contato)) {
                        redisPublisher.publish("chat:" + contato, JsonUtils.toJson(payload));
                    }
                }
            }, taskExecutor).exceptionally(ex -> {
                log.error("Erro ao notificar contatos após desconexão de {}", email, ex);
                return null;
            });
        } else {
            log.warn("❌ Token inválido ao tentar processar desconexão");
        }

        super.afterConnectionClosed(session, status);
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

        ChatMessage mensagem = (ChatMessage) JsonUtils.toObject(payload, ChatMessage.class);
        mensagem.setFrom(remetenteEmail);

        redisPublisher.publish("chat:" + destinatarioEmail, JsonUtils.toJson(mensagem));
        log.info("📤 Publicando no canal 'chat:{}'", destinatarioEmail);

        if (!userSessionManager.isUserOnline(destinatarioEmail)) {
            // Salva mensagem offline async sem bloquear
            messageService.salvarMensagemOffline(mensagem).exceptionally(ex -> {
                log.error("Erro ao salvar mensagem offline para {}", destinatarioEmail, ex);
                return null;
            });
            log.info("💾 Destinatário offline (global), mensagem salva no DynamoDB.");
        }
    }

    private void removerSessao(WebSocketSession session) {
        String token = extrairToken(session);
        if (token == null) {
            log.warn("Não foi possível extrair token para remover sessão.");
            return;
        }

        String email = tokenUtils.getEmailFromToken(token);
        if (email == null) {
            log.warn("Token inválido ao remover sessão.");
            return;
        }

        Set<WebSocketSession> sessSet = sessions.get(email);
        if (sessSet != null) {
            boolean removed = sessSet.remove(session);
            if (removed) {
                log.info("Sessão removida do usuário {}. Restantes: {}", email, sessSet.size());
                if (sessSet.isEmpty()) {
                    sessions.remove(email);
                    userSessionManager.userDisconnected(email);
                    log.info("Usuário {} não possui mais sessões ativas e foi removido do mapa.", email);
                }
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("Erro transporte WebSocket na sessão {}: {}", session.getId(), exception.getMessage(), exception);
        removerSessao(session);
        session.close(CloseStatus.SERVER_ERROR.withReason("Erro no transporte"));
    }

    public Set<WebSocketSession> getSessions(String email) {
        return sessions.getOrDefault(email, Collections.emptySet());
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
