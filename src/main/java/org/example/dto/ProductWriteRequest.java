package org.example.dto;

import java.math.BigDecimal;

public record ProductWriteRequest(
        String name,
        Integer brandId,
        Integer categoryId,
        BigDecimal price
) {

    public ProductWriteRequest {
        if(name == null || name.isBlank()){
            throw new IllegalArgumentException("Product name is required");
        }

        if(brandId == null || brandId <= 0){
            throw new IllegalArgumentException("A valid ID is required");
        }
        if(categoryId == null || categoryId <= 0){
            throw new IllegalArgumentException("A valid category is required");
        }
        if(price == null || price.compareTo(BigDecimal.ZERO) <= 0){
            throw new IllegalArgumentException("Price must be greater than zero");
        }
    }
}