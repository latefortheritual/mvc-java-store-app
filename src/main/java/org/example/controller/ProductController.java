package org.example.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.dto.CreateProductRequest;
import org.example.exception.DataAccessException;
import org.example.model.Product;
import org.example.service.ProductService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ProductController extends HttpServlet {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 100;
    private static final int MAX_SIZE = 1000;
    private static final int MAX_DIGITS = 9;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        int page = parsePositiveIntOrDefault(req, "page", DEFAULT_PAGE);
        int size = parsePositiveIntOrDefault(req, "size", DEFAULT_SIZE);

        if(size > MAX_SIZE){
            size = MAX_SIZE;
        }

        List<Product> products;
        try{
            products = productService.getProduct(page,size);
        }catch(DataAccessException e){
            resp.setStatus(503);
            resp.setContentType("application/json; charset=UTF-8");
            resp.getWriter().write(jsonMapper.writeValueAsString(
                    Map.of("error", "Database unavailable")
            ));
            return;
        }

        resp.setStatus(200);
        resp.setContentType("application/json; charset=UTF-8");
        resp.getWriter().write(jsonMapper.writeValueAsString(products));

    }

    private int parsePositiveIntOrDefault(HttpServletRequest request, String parameterName, int defaultValue){
        int result = defaultValue;

        String text = request.getParameter(parameterName);
        if(isPositiveNaturalNumber(text)){
            result = Integer.parseInt(text);
        }

        return result;
    }

    private boolean isPositiveNaturalNumber (String text){
        if(text == null || text.isEmpty()){
            return false;
        }
        if(text.length() > MAX_DIGITS){
            return false;
        }
        for(char character : text.toCharArray()){
            if(!Character.isDigit(character)){
                return false;
            }
        }
        return Integer.parseInt(text) > 0;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        CreateProductRequest createProductRequest;
        try {
            createProductRequest = jsonMapper.readValue(req.getReader(), CreateProductRequest.class);
        } catch (JacksonException e) {
            resp.setStatus(400);
            resp.setContentType("application/json; charset=UTF-8");
            resp.getWriter().write(jsonMapper.writeValueAsString(
                    Map.of("error", "Invalid request body")
            ));
            return;
        }

        Product createdProduct = productService.createProduct(createProductRequest);

        resp.setStatus(201);
        resp.setContentType("application/json; charset=UTF-8");
        resp.getWriter().write(jsonMapper.writeValueAsString(createdProduct));

    }
}