package com.messenger.chat.services;

import com.messenger.chat.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final DynamoDbClient dynamoDb;

    private static final String TABLE_NAME = "mensagens_pendentes";

    // Executor com virtual threads
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public CompletableFuture<Void> salvarMensagemOffline(ChatMessage mensagem) {
        return CompletableFuture.runAsync(() -> {
            Map<String, AttributeValue> item = Map.of(
                    "destinatario_email", AttributeValue.fromS(mensagem.getTo()),
                    "timestamp", AttributeValue.fromS(Instant.now().toString()),
                    "remetente_email", AttributeValue.fromS(mensagem.getFrom()),
                    "conteudo", AttributeValue.fromS(mensagem.getConteudo()),
                    "entregue", AttributeValue.fromBool(false)
            );

            PutItemRequest request = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(item)
                    .build();

            dynamoDb.putItem(request);
            log.info("💾 Mensagem salva offline para {}", mensagem.getTo());
        }, executor);
    }

    public CompletableFuture<List<ChatMessage>> buscarMensagensOffline(String email) {
        return CompletableFuture.supplyAsync(() -> {
            QueryRequest request = QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .keyConditionExpression("destinatario_email = :email")
                    .expressionAttributeValues(Map.of(
                            ":email", AttributeValue.fromS(email),
                            ":false", AttributeValue.fromBool(false)
                    ))
                    .filterExpression("entregue = :false")
                    .build();

            QueryResponse response = dynamoDb.query(request);
            List<ChatMessage> mensagens = new ArrayList<>();

            for (var item : response.items()) {
                ChatMessage msg = new ChatMessage();
                msg.setTo(item.get("destinatario_email").s());
                msg.setFrom(item.get("remetente_email").s());
                msg.setConteudo(item.get("conteudo").s());
                mensagens.add(msg);
            }
            return mensagens;
        }, executor);
    }

    public CompletableFuture<Void> removerMensagensOffline(String email) {
        return CompletableFuture.runAsync(() -> {
            QueryRequest queryRequest = QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .keyConditionExpression("destinatario_email = :email")
                    .expressionAttributeValues(Map.of(":email", AttributeValue.fromS(email)))
                    .build();

            var response = dynamoDb.query(queryRequest);

            for (var item : response.items()) {
                String destinatario = item.get("destinatario_email").s();
                String timestamp = item.get("timestamp").s();

                DeleteItemRequest deleteRequest = DeleteItemRequest.builder()
                        .tableName(TABLE_NAME)
                        .key(Map.of(
                                "destinatario_email", AttributeValue.fromS(destinatario),
                                "timestamp", AttributeValue.fromS(timestamp)
                        ))
                        .build();

                dynamoDb.deleteItem(deleteRequest);
                log.info("🗑️ Mensagem offline deletada para {}", destinatario);
            }
        }, executor);
    }
}