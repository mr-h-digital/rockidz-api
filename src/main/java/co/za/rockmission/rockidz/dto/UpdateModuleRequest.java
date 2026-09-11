package co.za.rockmission.rockidz.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateModuleRequest(
        @NotBlank String title
) {
}
