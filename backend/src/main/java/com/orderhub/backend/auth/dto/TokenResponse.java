package com.orderhub.backend.auth.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
}