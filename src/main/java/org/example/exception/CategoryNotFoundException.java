package org.example.exception;

public class CategoryNotFoundException extends RuntimeException {
    public CategoryNotFoundException(int categoryId) {

        super("Category ID " + categoryId + " not found");
    }
}
