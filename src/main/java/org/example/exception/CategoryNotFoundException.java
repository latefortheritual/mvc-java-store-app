package org.example.exception;

public class CategoryNotFoundException extends NotFoundException {
    public CategoryNotFoundException(int categoryId) {

        super("Category ID " + categoryId + " not found");
    }
}
