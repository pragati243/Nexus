package com.nexus.security.devauth;

import jakarta.validation.constraints.NotBlank;

public record DevTokenRequest(@NotBlank String subject) {
}
