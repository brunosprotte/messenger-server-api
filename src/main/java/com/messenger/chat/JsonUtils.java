package com.messenger.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonUtils {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static String extractField(String field, String jsonPayload) {
        try {
            JsonNode root = mapper.readTree(jsonPayload);
            JsonNode fieldNode = root.get(field);

            if (fieldNode != null && fieldNode.isTextual()) {
                return fieldNode.asText();
            }
            return null; // ou lançar exceção, dependendo do caso
        } catch (Exception e) {
            e.printStackTrace();
            return null; // ou tratar o erro de outra forma
        }
    }

    public static Object toObject(String payload, Class clazz) {
        try {
            return mapper.readValue(payload, clazz);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String toJson(Object object) {
        try {
            return mapper.writeValueAsString(object);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}

