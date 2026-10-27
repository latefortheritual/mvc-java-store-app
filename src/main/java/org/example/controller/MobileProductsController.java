package org.example.controller;

import org.example.dto.MobileProductsResponse;
import org.example.framework.annotation.Controller; // [router-6] marchează clasa ca controller și îi dă path-ul de bază
import org.example.framework.annotation.Get; // [router-6] adnotarea pentru GET
import org.example.service.ProductService;

@Controller("/mobile/products") // [router-6] o rută cu două segmente fixe, imposibilă cu vechea convenție de nume
public class MobileProductsController { // [router-6] nu mai extinde HttpServlet: e un controller simplu, ca ProductsController

    private final ProductService productService;

    public MobileProductsController(ProductService productService) {
        this.productService = productService;
    }

    @Get // [router-6] GET /mobile/products
    public MobileProductsResponse getAll() { // [router-6] înainte era doGet; acum întoarce obiectul, iar dispatcher-ul scrie JSON-ul
        MobileProductsResponse mobileProducts = productService.getMobileProducts();

        return mobileProducts; // [router-6] statusul 200 și JSON-ul le pune dispatcher-ul
    } // [router-6] sfârșitul metodei getAll
}
