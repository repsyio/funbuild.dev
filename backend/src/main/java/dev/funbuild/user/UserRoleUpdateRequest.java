package dev.funbuild.user;

import jakarta.validation.constraints.NotNull;

public record UserRoleUpdateRequest(@NotNull Role role) {}
