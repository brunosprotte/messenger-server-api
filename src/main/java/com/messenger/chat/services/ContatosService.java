package com.messenger.chat.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContatosService {

    private final DynamoDbClient dynamoDb;

    private static final String TABELA_CONTATOS = "contatos";

    private final Executor taskExecutor;  // injetar o executor de virtual threads

    public CompletableFuture<List<String>> listarContatos(String usuarioEmail) {
        return CompletableFuture.supplyAsync(() -> {
            QueryRequest request = QueryRequest.builder()
                    .tableName(TABELA_CONTATOS)
                    .keyConditionExpression("usuario_email = :usuario")
                    .expressionAttributeValues(Map.of(":usuario", AttributeValue.fromS(usuarioEmail)))
                    .projectionExpression("contato_email")
                    .build();

            QueryResponse response = dynamoDb.query(request);

            if (response.count() == 0) {
                log.info("👤 Usuário {} não possui contatos cadastrados", usuarioEmail);
                return Collections.<String>emptyList();
            }

            List<String> contatos = response.items().stream()
                    .map(item -> item.get("contato_email").s())
                    .collect(Collectors.toList());

            log.info("📒 {} contatos encontrados para {}", contatos.size(), usuarioEmail);
            return contatos;
        }, taskExecutor);
    }
}
