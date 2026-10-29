package org.example.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.dto.ErrorResponse;
import org.example.dto.PatchProductRequest;
import org.example.dto.ProductWriteRequest;
import org.example.model.Product;
import org.example.service.ProductService;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

public class ProductController extends HttpServlet {
    private static final int MAX_DIGITS = 9;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String pathInfo = request.getPathInfo(); // "/id"

        if(pathInfo == null || pathInfo.equals("/")){
            sendError(response, 400, "Bad Request", "Invalid request body");
            return;
        }
        String productIdText = pathInfo.substring(1);

        if(productIdText.contains("/") || !isPositiveNaturalNumber(productIdText)){
            sendError(response, 400, "Bad Request", "Invalid request body");
            return;
        }

        int productId = Integer.parseInt(productIdText);
        Product product = productService.getProductById(productId);


        response.setStatus(200);
        response.setContentType("application/json; charset=UTF-8");
        response.getWriter().write(jsonMapper.writeValueAsString(product));
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String pathInfo = request.getPathInfo(); // "/id"

        if(pathInfo == null || pathInfo.equals("/")){
            sendError(response, 400, "Bad Request", "Invalid request body");
            return;
        }
        String productIdText = pathInfo.substring(1);

        if(productIdText.contains("/") || !isPositiveNaturalNumber(productIdText)){
            sendError(response, 400, "Bad Request", "Invalid request body");
            return;
        }

        int productId = Integer.parseInt(productIdText);
        Product product = productService.deleteProductById(productId);

        response.setStatus(200);
        response.setContentType("application/json; charset=UTF-8");
        response.getWriter().write(jsonMapper.writeValueAsString(product));
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String pathInfo = request.getPathInfo(); // "/id"

        if(pathInfo == null || pathInfo.equals("/")){
            sendError(response, 400, "Bad Request", "Invalid request body");
            return;
        }
        String productIdText = pathInfo.substring(1);

        if(productIdText.contains("/") || !isPositiveNaturalNumber(productIdText)){
            sendError(response, 400, "Bad Request", "Invalid request body");
            return;
        }

        int productId = Integer.parseInt(productIdText);

        ProductWriteRequest productWriteRequest = jsonMapper.readValue(request.getReader(), ProductWriteRequest.class);
        Product product = productService.updateProductById(productId, productWriteRequest);


        response.setStatus(200);
        response.setContentType("application/json; charset=UTF-8");
        response.getWriter().write(jsonMapper.writeValueAsString(product));
    }

    @Override
    protected void doPatch(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String pathInfo = request.getPathInfo(); // "/id"

        if(pathInfo == null || pathInfo.equals("/")){
            sendError(response, 400, "Bad Request", "Invalid request body");
            return;
        }
        String productIdText = pathInfo.substring(1);

        if(productIdText.contains("/") || !isPositiveNaturalNumber(productIdText)){
            sendError(response, 400, "Bad Request", "Invalid request body");
            return;
        }

        int productId = Integer.parseInt(productIdText);

        PatchProductRequest patchProductRequest = jsonMapper.readValue(request.getReader(), PatchProductRequest.class);
        Product product = productService.updateProductFieldsById(productId, patchProductRequest);

        response.setStatus(200);
        response.setContentType("application/json; charset=UTF-8");
        response.getWriter().write(jsonMapper.writeValueAsString(product));
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
