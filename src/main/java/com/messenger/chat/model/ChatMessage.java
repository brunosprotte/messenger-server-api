package com.messenger.chat.model;

public record ChatMessage(
        String to,
        String from,
        String conteudo
) {}