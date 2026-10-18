package org.example.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.exception.DataAccessException;
import org.example.service.ProductService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.List;

import static org.mockito.Mockito.mock;

public class TestProductController {


    @Test
    void requested_product_count_is_limited_to_the_maximum_allowed_size() throws IOException, ServletException {

        //Arrage
        HttpServletRequest request = mock(HttpServletRequest.class);
        Mockito.when(request.getParameter("page")).thenReturn("2");
        Mockito.when(request.getParameter("size")).thenReturn("1001");
        ProductService productService = mock(ProductService.class);
        ProductController productController = new ProductController(productService);

        HttpServletResponse response = mock(HttpServletResponse.class);
        PrintWriter writer = mock(PrintWriter.class);
        Mockito.when(response.getWriter())
                .thenReturn(writer);
        Mockito.when(productService.getProduct(Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(List.of());

        //Act
        productController.doGet(request, response);

        //Assert
        Mockito.verify(productService).getProduct(2, 1000);
    }

    @Test
    void databaseUnavailability_is_reported_to_the_client() throws IOException, ServletException {

        //Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);

        Mockito.when(request.getParameter("page")).thenReturn("2");
        Mockito.when(request.getParameter("size")).thenReturn("10");

        ProductService productService = mock(ProductService.class);
        SQLException cause = new SQLException("Database connection failed");

        Mockito.when(productService.getProduct(2, 10)).thenThrow(new DataAccessException("Could not load products", cause));

        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        Mockito.when(response.getWriter()).thenReturn(printWriter);

        ProductController productController = new ProductController(productService);

        //Act
        productController.doGet(request,response);

        //Assert
        Mockito.verify(response).setStatus(503);
        Mockito.verify(response).setContentType("application/json; charset=UTF-8");

        // printWriter.flush();
        String responseBody = stringWriter.toString();
        Assertions.assertTrue(responseBody.contains("Database unavailable"));
    }
}