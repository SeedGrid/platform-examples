package br.com.seedgrid.privacyexample.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload de criacao. Os valores chegam em CLARO (o cliente digita o CPF/e-mail normais); a
 * criptografia at-rest acontece de forma transparente na hora de persistir a entidade.
 */
public record CustomerRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank String cpf,
        String phone) {
}
