package org.example.exception;

public class BrandNotFoundException extends NotFoundException {
    public BrandNotFoundException(int brandId) {
        super("Brand ID " + brandId + " not found");
    }
}
