package co.za.rockmission.rockidz.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateProgressRequest(
        @NotNull @PositiveOrZero Integer watchTimeSeconds,
        boolean markComplete
) {}
