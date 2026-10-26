package org.example.controller;

import org.example.exception.DataAccessException;
import org.example.framework.RequestParams; // [router-5] controllerul primește parametrii prin RequestParams
import org.example.service.ProductService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.util.List;
import java.util.Map; // [router-5] RequestParams se construiește dintr-un map simplu

import static org.mockito.Mockito.mock;

public class TestProductsController {


    @Test
    void requested_product_count_is_limited_to_the_maximum_allowed_size() { // [router-5] fără IOException și ServletException: controllerul nu mai atinge HTTP-ul

        //Arrage
        RequestParams params = new RequestParams(Map.of("page", new String[]{"2"}, "size", new String[]{"1001"})); // [router-5] parametrii construiți direct, fără mock de HttpServletRequest
        ProductService productService = mock(ProductService.class);
        ProductsController productsController = new ProductsController(productService);

        Mockito.when(productService.getProduct(Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(List.of());

        //Act
        productsController.getAll(params); // [router-5] apelăm metoda nouă, nu doGet

        //Assert
        Mockito.verify(productService).getProduct(2, 1000);
    }

    @Test
    void databaseUnavailability_is_passed_on_to_the_exception_filter() { // [router-5] controllerul nu mai scrie răspunsul de eroare; GlobalExceptionFilter îl transformă în 503

        //Arrange
        RequestParams params = new RequestParams(Map.of("page", new String[]{"2"}, "size", new String[]{"10"})); // [router-5] aceiași parametri ca în testul vechi

        ProductService productService = mock(ProductService.class);
        SQLException cause = new SQLException("Database connection failed");

        Mockito.when(productService.getProduct(2, 10)).thenThrow(new DataAccessException("Could not load products", cause));

        ProductsController productsController = new ProductsController(productService);

        //Act
        DataAccessException thrown = Assertions.assertThrows(DataAccessException.class, () -> productsController.getAll(params)); // [router-5] excepția trece neschimbată prin controller

        //Assert
        Assertions.assertEquals("Could not load products", thrown.getMessage()); // [router-5] e chiar excepția aruncată de serviciu
    }
}