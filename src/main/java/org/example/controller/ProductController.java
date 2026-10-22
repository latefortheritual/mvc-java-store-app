package org.example.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.dto.CreateProductRequest;
import org.example.dto.ErrorResponse;
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
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        int page = parsePositiveIntOrDefault(request, "page", DEFAULT_PAGE);
        int size = parsePositiveIntOrDefault(request, "size", DEFAULT_SIZE);

        if(size > MAX_SIZE){
            size = MAX_SIZE;
        }

        List<Product> products;
        try{
            products = productService.getProduct(page,size);
        }catch(DataAccessException e){
            sendError(response, 503, "Service Unavailable", "Database unavailable");
            return;
        }

        response.setStatus(200);
        response.setContentType("application/json; charset=UTF-8");
        response.getWriter().write(jsonMapper.writeValueAsString(products));

    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        CreateProductRequest createProductRequest;
        try {
            createProductRequest = jsonMapper.readValue(request.getReader(), CreateProductRequest.class);
        } catch (JacksonException e) {
            sendError(response, 400, "Bad Request", "Invalid request body");
            return;
        }

        Product createdProduct = productService.createProduct(createProductRequest);

        response.setStatus(201);
        response.setContentType("application/json; charset=UTF-8");
        response.getWriter().write(jsonMapper.writeValueAsString(createdProduct));

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

    private void sendError(HttpServletResponse response, int statusCode, String error, String message) throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json; charset=UTF-8");
        ErrorResponse errorResponse = new ErrorResponse(statusCode, error, message);
        response.getWriter().write(jsonMapper.writeValueAsString(errorResponse));
    }
}