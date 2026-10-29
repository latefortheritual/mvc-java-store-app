package org.example.service;

import org.example.model.Product;

import java.util.List;
import java.util.Map;

public record ProductPage(
        List<Product> products,
        Map<Integer, String> brands,
        Map<Integer, String> categories,
        long total,
        int limit,
        int offset
) {
}
