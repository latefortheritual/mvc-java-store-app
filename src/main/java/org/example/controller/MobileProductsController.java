package org.example.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.dto.MobileProductsResponse;
import org.example.service.ProductService;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

public class MobileProductsController extends HttpServlet {
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private final ProductService productService;

    public MobileProductsController(ProductService productService) {
        this.productService = productService;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        MobileProductsResponse mobileProducts = productService.getMobileProducts();

        resp.setStatus(200);
        resp.setContentType("application/json; charset=UTF-8");
        resp.getWriter().write(jsonMapper.writeValueAsString(mobileProducts));
    }
}
