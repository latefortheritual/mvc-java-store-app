package org.example.dto;

import java.math.BigDecimal;

public record ProductWithBrandAndCategoryRow(
        int productId,
        String productName,
        BigDecimal price,
        int brandId,
        String brandName,
        int categoryId,
        String categoryName
) {
}
