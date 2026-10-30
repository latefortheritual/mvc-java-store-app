package org.example.controller;

import org.example.exception.DataAccessException;
import org.example.framework.RequestParams;
import org.example.service.ProductService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;

public class TestProductsController {


    @Test
    void requested_product_count_is_limited_to_the_maximum_allowed_size() {

        //Arrage
        RequestParams params = new RequestParams(Map.of(
                "page", new String[]{"2"},
                "size", new String[]{"1001"}
        ));
        ProductService productService = mock(ProductService.class);
        ProductsController productsController = new ProductsController(productService);

        Mockito.when(productService.getProduct(Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(List.of());

        //Act
        productsController.getAll(params);

        //Assert
        Mockito.verify(productService).getProduct(2, 1000);
    }

    @Test
    void databaseUnavailability_is_passed_on_to_the_exception_filter() {

        //Arrange
        RequestParams params = new RequestParams(Map.of(
                "page", new String[]{"2"},
                "size", new String[]{"10"}
        ));

        ProductService productService = mock(ProductService.class);
        SQLException cause = new SQLException("Database connection failed");

        Mockito.when(productService.getProduct(2, 10)).thenThrow(new DataAccessException("Could not load products", cause));

        ProductsController productsController = new ProductsController(productService);

        //Act + Assert
        // controllerul nu mai scrie răspunsul; GlobalExceptionFilter transformă excepția în 503
        Assertions.assertThrows(DataAccessException.class, () -> productsController.getAll(params));
    }
}
