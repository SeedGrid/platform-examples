package br.com.seedgrid.whatsappexample.dto;

import java.time.Instant;

import br.com.seedgrid.whatsappexample.entity.NotificationStatus;

public record NotificationResponseDTO(
        Long id,
        String phoneNumber,
        String message,
        String channelId,
        NotificationStatus status,
        String providerMessageId,
        String errorMessage,
        Instant createdAt) {
}
