package co.za.rockmission.learn.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateProgressRequest(
        @NotNull @PositiveOrZero Integer watchTimeSeconds,
        boolean markComplete
) {}
