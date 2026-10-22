package org.example.dto;

public record ErrorResponse(int statusCode, String error, String message) {
}
