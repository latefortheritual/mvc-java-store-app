package org.example.controller;

import org.example.dto.PatchProductRequest; // [router-5] body-ul pentru patch, preluat din fostul ProductController
import org.example.dto.ProductWriteRequest;
import org.example.framework.RequestParams; // [router-5] parametrii din query string, fără HttpServletRequest
import org.example.framework.annotation.Controller; // [router-6] marchează clasa ca controller și îi dă path-ul de bază
import org.example.framework.annotation.Delete; // [router-6] adnotarea pentru DELETE
import org.example.framework.annotation.Get; // [router-6] adnotarea pentru GET
import org.example.framework.annotation.Patch; // [router-6] adnotarea pentru PATCH
import org.example.framework.annotation.PathVariable; // [router-6] leagă parametrul id de variabila {id} din șablon
import org.example.framework.annotation.Post; // [router-6] adnotarea pentru POST
import org.example.framework.annotation.Put; // [router-6] adnotarea pentru PUT
import org.example.framework.annotation.ResponseStatus; // [router-6] statusul 201 pentru create
import org.example.model.Product;
import org.example.service.ProductService;

import java.util.List;

@Controller("/products") // [router-6] toate rutele clasei încep cu /products; numele clasei nu mai contează pentru rutare
public class ProductsController { // [router-5] nu mai extinde HttpServlet: e o clasă simplă, după regulile noastre

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 100;
    private static final int MAX_SIZE = 1000;
    private static final int MAX_DIGITS = 9;

    private final ProductService productService;

    public ProductsController(ProductService productService) {
        this.productService = productService;
    }

    @Get // [router-6] GET /products; fără valoare, ruta este chiar path-ul controllerului
    public List<Product> getAll(RequestParams params) { // [router-5] GET /products; înainte era doGet
        int page = parsePositiveIntOrDefault(params, "page", DEFAULT_PAGE); // [router-5] citim din RequestParams, nu din request
        int size = parsePositiveIntOrDefault(params, "size", DEFAULT_SIZE); // [router-5] la fel pentru size

        if(size > MAX_SIZE){
            size = MAX_SIZE;
        }

        List<Product> products = productService.getProduct(page,size);
        return products; // [router-5] întoarcem obiectul; dispatcher-ul îl scrie ca JSON
    } // [router-5] sfârșitul metodei getAll

    @Get("/{id}") // [router-6] GET /products/{id}
    public Product getOne(@PathVariable("id") int id) { // [router-6] @PathVariable leagă parametrul de {id}; valoarea vine validată de dispatcher
        return productService.getProductById(id); // [router-5] dacă produsul nu există, serviciul aruncă ProductNotFoundException, adică 404
    } // [router-5] sfârșitul metodei getOne

    @Post // [router-6] POST /products
    @ResponseStatus(201) // [router-6] 201 Created; înainte, statusul era scris într-un tabel din dispatcher
    public Product create(ProductWriteRequest body) { // [router-5] POST /products; înainte era doPost
        return productService.createProduct(body); // [router-6] statusul 201 vine din @ResponseStatus
    } // [router-5] sfârșitul metodei create

    @Put("/{id}") // [router-6] PUT /products/{id}
    public Product update(@PathVariable("id") int id, ProductWriteRequest body) { // [router-6] id-ul din path, body-ul din JSON
        return productService.updateProductById(id, body); // [router-5] înlocuiește tot produsul
    } // [router-5] sfârșitul metodei update

    @Patch("/{id}") // [router-6] PATCH /products/{id}
    public Product patch(@PathVariable("id") int id, PatchProductRequest body) { // [router-6] id-ul din path, body-ul din JSON
        return productService.updateProductFieldsById(id, body); // [router-5] schimbă doar câmpurile trimise
    } // [router-5] sfârșitul metodei patch

    @Delete("/{id}") // [router-6] DELETE /products/{id}
    public Product delete(@PathVariable("id") int id) { // [router-6] id-ul din path
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