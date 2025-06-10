package com.messenger.chat.services;

import com.messenger.chat.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final DynamoDbClient dynamoDb;

    private static final String TABLE_NAME = "mensagens_pendentes";

    public void salvarMensagemOffline(ChatMessage mensagem) {
        Map<String, AttributeValue> item = Map.of(
                "destinatario_email", AttributeValue.fromS(mensagem.to()),
                "timestamp", AttributeValue.fromS(Instant.now().toString()),
                "remetente_email", AttributeValue.fromS(mensagem.from()),
                "conteudo", AttributeValue.fromS(mensagem.conteudo()),
                "entregue", AttributeValue.fromBool(false)
        );

        PutItemRequest request = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build();

        dynamoDb.putItem(request);
    }
}
