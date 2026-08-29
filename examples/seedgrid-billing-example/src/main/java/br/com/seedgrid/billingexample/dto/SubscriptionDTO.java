package br.com.seedgrid.billingexample.dto;

import java.time.Instant;

public record SubscriptionDTO(
        String planCode,
        String planName,
        String status,
        String providerSubscriptionId,
        Instant currentPeriodEnd,
        Instant canceledAt) {
}
