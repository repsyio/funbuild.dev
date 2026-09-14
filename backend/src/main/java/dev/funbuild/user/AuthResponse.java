package dev.funbuild.user;

public record AuthResponse(String token, UserSummary user) {}
