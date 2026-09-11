package co.za.rockmission.rockidz.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateModuleRequest(
        @NotBlank String title,
        Integer orderIndex
) {}
