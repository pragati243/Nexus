package com.nexus.security.devauth;

public record DevTokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
