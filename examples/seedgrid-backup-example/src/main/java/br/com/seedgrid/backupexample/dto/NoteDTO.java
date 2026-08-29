package br.com.seedgrid.backupexample.dto;

import jakarta.validation.constraints.NotBlank;

public record NoteDTO(Long id, @NotBlank String title, String content) {
}
