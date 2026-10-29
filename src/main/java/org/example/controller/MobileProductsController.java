package org.example.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.dto.MobileProductsResponse;
import org.example.dto.PaginationMeta;
import org.example.service.ProductPage;
import org.example.exception.BadRequestException;
import org.example.service.ProductService;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

public class MobileProductsController extends HttpServlet {
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private static final int DEFAULT_LIMIT = 100;
    private static final int DEFAULT_OFFSET = 0;

    private final ProductService productService;

    public MobileProductsController(ProductService productService) {

        this.productService = productService;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        int limit = parseIntegerParameter(req.getParameter("limit"), "limit", DEFAULT_LIMIT);
        int offset = parseIntegerParameter(req.getParameter("offset"), "offset", DEFAULT_OFFSET);

        ProductPage page = productService.getProducts(offset, limit);

        PaginationMeta meta = new PaginationMeta(page.total(), page.limit(), page.offset());

        MobileProductsResponse response = new MobileProductsResponse(page.products(), page.brands(), page.categories(), meta);

        resp.setStatus(200);
        resp.setContentType("application/json; charset=UTF-8");
        resp.getWriter().write(jsonMapper.writeValueAsString(response));
    }

    private int parseIntegerParameter(String text, String parameterName, int defaultValue) {
        if (text == null) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new BadRequestException(parameterName + " must be a valid 32-bit integer");
        }
    }
}