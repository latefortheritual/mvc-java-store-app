package org.example.dto;

import java.math.BigDecimal;

public record PatchProductRequest(
        String name,
        Integer brandId,
        Integer categoryId,
        BigDecimal price
) {

    public PatchProductRequest {
        if(name == null && brandId == null && categoryId == null && price == null){
            throw new IllegalArgumentException("At least one field must be provided");
        }
        if(name != null && name.isBlank()){
            throw new IllegalArgumentException("Product name cannot be blank");
        }

        if(brandId != null && brandId <= 0){
            throw new IllegalArgumentException("Brand ID must be positive");
        }
        if(categoryId != null && categoryId <= 0){
            throw new IllegalArgumentException("Category ID must be positive");
        }
        if(price != null && price.compareTo(BigDecimal.ZERO) <= 0){
            throw new IllegalArgumentException("Price must be positive");
        }
    }
}