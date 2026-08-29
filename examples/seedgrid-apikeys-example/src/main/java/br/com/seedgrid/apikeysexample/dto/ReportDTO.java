package br.com.seedgrid.apikeysexample.dto;

import jakarta.validation.constraints.NotBlank;

public record ReportDTO(Long id, @NotBlank String title, String content) {
}
