package org.example.dto;

public record PaginationMeta(
        long total,
        int limit,
        int offset
) {
}
