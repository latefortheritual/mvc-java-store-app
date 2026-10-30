package org.example.controller;

import org.example.annotation.Log;
import org.example.dto.PatchProductRequest;
import org.example.dto.ProductWriteRequest;
import org.example.framework.RequestParams;
import org.example.model.Product;
import org.example.service.ProductService;

import java.util.List;

// un singur controller pentru toată resursa /products: colecția și fiecare produs
public class ProductsController {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 100;
    private static final int MAX_SIZE = 1000;
    private static final int MAX_DIGITS = 9;

    private final ProductService productService;

    public ProductsController(ProductService productService) {
        this.productService = productService;
    }

    // GET /products
    @Log
    public List<Product> getAll(RequestParams params) {
        int page = parsePositiveIntOrDefault(params, "page", DEFAULT_PAGE);
        int size = parsePositiveIntOrDefault(params, "size", DEFAULT_SIZE);

        if(size > MAX_SIZE){
            size = MAX_SIZE;
        }

        return productService.getProduct(page,size);
    }

    // GET /products/{id}
    public Product getOne(int id) {
        return productService.getProductById(id);
    }

    // POST /products
    public Product create(ProductWriteRequest createProductRequest) {
        return productService.createProduct(createProductRequest);
    }

    // PUT /products/{id}
    public Product update(int id, ProductWriteRequest productWriteRequest) {
        return productService.updateProductById(id, productWriteRequest);
    }

    // PATCH /products/{id}
    public Product patch(int id, PatchProductRequest patchProductRequest) {
        return productService.updateProductFieldsById(id, patchProductRequest);
    }

    // DELETE /products/{id}
    public Product delete(int id) {
        return productService.deleteProductById(id);
    }

    private int parsePositiveIntOrDefault(RequestParams params, String parameterName, int defaultValue){
        int result = defaultValue;

        String text = params.get(parameterName);
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
}
