package org.example.controller;

import org.example.dto.MobileProductsResponse;
import org.example.dto.PaginationMeta;
import org.example.framework.RequestParams;
import org.example.service.ProductPage;
import org.example.exception.BadRequestException;
import org.example.service.ProductService;

public class MobileProductsController {
    private static final int DEFAULT_LIMIT = 100;
    private static final int DEFAULT_OFFSET = 0;

    private final ProductService productService;

    public MobileProductsController(ProductService productService) {

        this.productService = productService;
    }

    // GET /mobile/products
    public MobileProductsResponse getAll(RequestParams params) {
        int limit = parseIntegerParameter(params.get("limit"), "limit", DEFAULT_LIMIT);
        int offset = parseIntegerParameter(params.get("offset"), "offset", DEFAULT_OFFSET);

        ProductPage page = productService.getProducts(offset, limit);

        PaginationMeta meta = new PaginationMeta(page.total(), page.limit(), page.offset());

        return new MobileProductsResponse(page.products(), page.brands(), page.categories(), meta);
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
