package dev.funbuild.assignment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record AssignmentRequest(
    @NotBlank @Size(max = 200) String title,
    @NotBlank String description,
    @NotNull Instant startAt,
    @NotNull Instant endAt) {}
