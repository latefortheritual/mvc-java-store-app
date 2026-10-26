package org.example.controller;

import org.example.dto.PatchProductRequest; // [router-5] body-ul pentru patch, preluat din fostul ProductController
import org.example.dto.ProductWriteRequest;
import org.example.framework.RequestParams; // [router-5] parametrii din query string, fără HttpServletRequest
import org.example.model.Product;
import org.example.service.ProductService;

import java.util.List;

public class ProductsController { // [router-5] nu mai extinde HttpServlet: e o clasă simplă, după regulile noastre

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 100;
    private static final int MAX_SIZE = 1000;
    private static final int MAX_DIGITS = 9;

    private final ProductService productService;

    public ProductsController(ProductService productService) {
        this.productService = productService;
    }

    public List<Product> getAll(RequestParams params) { // [router-5] GET /products; înainte era doGet
        int page = parsePositiveIntOrDefault(params, "page", DEFAULT_PAGE); // [router-5] citim din RequestParams, nu din request
        int size = parsePositiveIntOrDefault(params, "size", DEFAULT_SIZE); // [router-5] la fel pentru size

        if(size > MAX_SIZE){
            size = MAX_SIZE;
        }

        List<Product> products = productService.getProduct(page,size);
        return products; // [router-5] întoarcem obiectul; dispatcher-ul îl scrie ca JSON
    } // [router-5] sfârșitul metodei getAll

    public Product getOne(int id) { // [router-5] GET /products/123; id-ul vine deja validat de dispatcher
        return productService.getProductById(id); // [router-5] dacă produsul nu există, serviciul aruncă ProductNotFoundException, adică 404
    } // [router-5] sfârșitul metodei getOne

    public Product create(ProductWriteRequest body) { // [router-5] POST /products; înainte era doPost
        return productService.createProduct(body); // [router-5] dispatcher-ul răspunde cu 201 pentru create
    } // [router-5] sfârșitul metodei create

    public Product update(int id, ProductWriteRequest body) { // [router-5] PUT /products/123; preluat din fostul ProductController
        return productService.updateProductById(id, body); // [router-5] înlocuiește tot produsul
    } // [router-5] sfârșitul metodei update

    public Product patch(int id, PatchProductRequest body) { // [router-5] PATCH /products/123; preluat din fostul ProductController
        return productService.updateProductFieldsById(id, body); // [router-5] schimbă doar câmpurile trimise
    } // [router-5] sfârșitul metodei patch

    public Product delete(int id) { // [router-5] DELETE /products/123; preluat din fostul ProductController
        return productService.deleteProductById(id); // [router-5] întoarce produsul șters, ca înainte
    } // [router-5] sfârșitul metodei delete

    private int parsePositiveIntOrDefault(RequestParams params, String parameterName, int defaultValue){ // [router-5] primește RequestParams în loc de HttpServletRequest
        int result = defaultValue;

        String text = params.get(parameterName); // [router-5] același comportament ca request.getParameter()
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