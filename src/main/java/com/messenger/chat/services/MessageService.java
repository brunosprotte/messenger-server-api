package com.messenger.chat.services;

import com.messenger.chat.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MessageService {

    private static final DynamoDbClient dynamoDb = DynamoDbClient.builder()
            .endpointOverride(URI.create("http://127.0.0.1:4566"))
            .region(Region.US_EAST_1)
            .build();

    private static final String TABLE_NAME = "mensagens_pendentes";

    public void salvarMensagemOffline(ChatMessage mensagem) {
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
    }
}
