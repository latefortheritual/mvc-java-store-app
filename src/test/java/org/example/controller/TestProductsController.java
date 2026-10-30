package org.example.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.dto.ProductWriteRequest;
import org.example.exception.BadRequestException;
import org.example.exception.DataAccessException;
import org.example.model.Product;
import org.example.service.ProductService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

import static org.mockito.Mockito.mock;

public class TestProductsController {

    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter responseBody;
    private ProductService productService;
    private ProductsController productsController;

    @BeforeEach
    void setUp() throws IOException {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        responseBody = new StringWriter();
        Mockito.when(response.getWriter()).thenReturn(new PrintWriter(responseBody, true));
        productService = mock(ProductService.class);
        productsController = new ProductsController(productService);
    }

    @Test
    void requested_product_count_is_limited_to_the_maximum_allowed_size() throws IOException, ServletException {

        //Arrage
        Mockito.when(request.getParameter("page")).thenReturn("2");
        Mockito.when(request.getParameter("size")).thenReturn("1001");
        Mockito.when(productService.getProduct(Mockito.anyInt(), Mockito.anyInt(), Mockito.any(), Mockito.any()))
                .thenReturn(List.of());

        //Act
        productsController.doGet(request, response);

        //Assert
        Mockito.verify(productService).getProduct(2, 1000, null, null);
    }

    @Test
    void missing_or_invalid_pagination_uses_default_page_and_size() throws IOException, ServletException {

        //Arrange
        Mockito.when(request.getParameter("page")).thenReturn("abc");
        Mockito.when(request.getParameter("size")).thenReturn("9999999999");

        //Act
        productsController.doGet(request, response);

        //Assert
        Mockito.verify(productService).getProduct(1, 100, null, null);
    }

    @Test
    void products_are_written_as_json_with_status_200() throws IOException, ServletException {

        //Arrange
        Product product = new Product(1, "Product 1", 3, 5, new BigDecimal("305.95"));
        Mockito.when(productService.getProduct(1, 100, null, null)).thenReturn(List.of(product));

        //Act
        productsController.doGet(request, response);

        //Assert
        Mockito.verify(response).setStatus(200);
        Mockito.verify(response).setContentType("application/json; charset=UTF-8");
        Assertions.assertTrue(responseBody.toString().contains("\"name\":\"Product 1\""));
    }

    @Test
    void brand_filter_is_passed_to_the_service() throws IOException, ServletException {

        //Arrange
        Mockito.when(request.getParameter("brand_id")).thenReturn("3");

        //Act
        productsController.doGet(request, response);

        //Assert
        Mockito.verify(productService).getProduct(1, 100, 3, null);
    }

    @Test
    void category_filter_is_passed_to_the_service() throws IOException, ServletException {

        //Arrange
        Mockito.when(request.getParameter("category_id")).thenReturn("5");

        //Act
        productsController.doGet(request, response);

        //Assert
        Mockito.verify(productService).getProduct(1, 100, null, 5);
    }

    @Test
    void both_filters_are_combined_with_pagination() throws IOException, ServletException {

        //Arrange
        Mockito.when(request.getParameter("page")).thenReturn("2");
        Mockito.when(request.getParameter("size")).thenReturn("10");
        Mockito.when(request.getParameter("brand_id")).thenReturn("3");
        Mockito.when(request.getParameter("category_id")).thenReturn("5");

        //Act
        productsController.doGet(request, response);

        //Assert
        Mockito.verify(productService).getProduct(2, 10, 3, 5);
    }

    @Test
    void invalid_brand_filter_is_rejected() {

        //Arrange
        Mockito.when(request.getParameter("brand_id")).thenReturn("abc");

        //Act
        BadRequestException exception = Assertions.assertThrows(BadRequestException.class,
                () -> productsController.doGet(request, response));

        //Assert
        Assertions.assertEquals("brand_id must be a positive integer", exception.getMessage());
        Mockito.verifyNoInteractions(productService);
    }

    @Test
    void invalid_category_filter_is_rejected() {

        //Arrange
        Mockito.when(request.getParameter("category_id")).thenReturn("0");

        //Act
        BadRequestException exception = Assertions.assertThrows(BadRequestException.class,
                () -> productsController.doGet(request, response));

        //Assert
        Assertions.assertEquals("category_id must be a positive integer", exception.getMessage());
        Mockito.verifyNoInteractions(productService);
    }

    @Test
    void empty_filter_is_rejected() {

        //Arrange
        Mockito.when(request.getParameter("brand_id")).thenReturn("");

        //Act + Assert
        Assertions.assertThrows(BadRequestException.class, () -> productsController.doGet(request, response));
    }

    @Test
    void databaseUnavailability_is_passed_on_to_the_exception_filter() {

        //Arrange
        Mockito.when(request.getParameter("page")).thenReturn("2");
        Mockito.when(request.getParameter("size")).thenReturn("10");

        SQLException cause = new SQLException("Database connection failed");
        Mockito.when(productService.getProduct(2, 10, null, null)).thenThrow(new DataAccessException("Could not load products", cause));

        //Act + Assert
        // controllerul nu scrie răspunsul de eroare; GlobalExceptionFilter îl transformă în 503
        Assertions.assertThrows(DataAccessException.class, () -> productsController.doGet(request, response));
    }

    @Test
    void created_product_is_written_as_json_with_status_201() throws IOException, ServletException {

        //Arrange
        String body = "{\"name\":\"Product 1\",\"brandId\":3,\"categoryId\":5,\"price\":305.95}";
        Mockito.when(request.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
        Product createdProduct = new Product(7, "Product 1", 3, 5, new BigDecimal("305.95"));
        Mockito.when(productService.createProduct(Mockito.any())).thenReturn(createdProduct);

        //Act
        productsController.doPost(request, response);

        //Assert
        Mockito.verify(productService).createProduct(new ProductWriteRequest("Product 1", 3, 5, new BigDecimal("305.95")));
        Mockito.verify(response).setStatus(201);
        Assertions.assertTrue(responseBody.toString().contains("\"id\":7"));
    }
}
