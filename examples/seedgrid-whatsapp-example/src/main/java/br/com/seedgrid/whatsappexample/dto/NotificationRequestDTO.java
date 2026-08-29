package br.com.seedgrid.whatsappexample.dto;

import jakarta.validation.constraints.NotBlank;

public record NotificationRequestDTO(
        @NotBlank String phoneNumber,
        @NotBlank String message) {
}
