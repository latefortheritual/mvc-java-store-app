package org.example.service;

import org.example.model.Product;
import org.example.repository.BrandRepository;
import org.example.repository.CategoryRepository;
import org.example.repository.ProductRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.mock;

public class TestProductService {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ProductService productService = new ProductService(
            productRepository, mock(BrandRepository.class), mock(CategoryRepository.class));

    @Test
    void page_and_size_become_limit_and_offset_and_filters_are_passed_on() {

        //Arrange
        List<Product> products = List.of(new Product(1, "Product 1", 3, 5, new BigDecimal("305.95")));
        Mockito.when(productRepository.readProduct(10, 20L, 3, 5)).thenReturn(products);

        //Act
        List<Product> result = productService.getProduct(3, 10, 3, 5);

        //Assert
        Assertions.assertEquals(products, result);
    }

    @Test
    void missing_filters_are_passed_on_as_null() {

        //Act
        productService.getProduct(1, 100, null, null);

        //Assert
        Mockito.verify(productRepository).readProduct(100, 0L, null, null);
    }

    @Test
    void offset_of_a_very_large_page_does_not_overflow() {

        //Act
        productService.getProduct(999_999_999, 1000, null, null);

        //Assert
        // fără conversia la long, 999_999_998 * 1000 ar depăși int și ar deveni negativ
        Mockito.verify(productRepository).readProduct(1000, 999_999_998_000L, null, null);
    }
}
