package org.example.dto;

import org.example.model.Product;

import java.util.List;
import java.util.Map;

public record MobileProductsResponse(
        List<Product> products,
        Map<Integer, String> brands,
        Map<Integer, String> categories
) {
}
