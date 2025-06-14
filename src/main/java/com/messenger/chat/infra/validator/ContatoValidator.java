package com.messenger.chat.infra.validator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContatoValidator {

    private static final String TABELA_CONTATOS = "contatos";

    private final DynamoDbClient dynamoDb;

    /**
     * Verifica se o usuário remetente pode enviar mensagens ao destinatário.
     *
     * @param remetenteEmail    email do usuário que está enviando a mensagem
     * @param destinatarioEmail email do usuário que deve receber a mensagem
     * @return true se o contato existir, for aceito e não estiver bloqueado
     */
    public boolean podeConversar(String remetenteEmail, String destinatarioEmail) {
        Map<String, AttributeValue> key = Map.of(
                "usuario_email", AttributeValue.fromS(remetenteEmail),
                "contato_email", AttributeValue.fromS(destinatarioEmail)
        );

        GetItemRequest request = GetItemRequest.builder()
                .tableName(TABELA_CONTATOS)
                .key(key)
                .build();

        GetItemResponse response = dynamoDb.getItem(request);

        if (!response.hasItem()) {
            log.info("Contato entre {} e {} não encontrado.", remetenteEmail, destinatarioEmail);
            return false;
        }

        Map<String, AttributeValue> item = response.item();

        boolean aceito = item.containsKey("aceito") && item.get("aceito").bool();
        boolean bloqueado = item.containsKey("bloqueado") && item.get("bloqueado").bool();

        return aceito && !bloqueado;
    }
}
