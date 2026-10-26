package org.example.exception;

public class ProductNotFoundException extends NotFoundException {
    public ProductNotFoundException(int productId) {

      super("Product ID " + productId + " not found");
    }
}
