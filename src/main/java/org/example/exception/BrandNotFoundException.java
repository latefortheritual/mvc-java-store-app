package org.example.exception;

public class BrandNotFoundException extends RuntimeException {
    public BrandNotFoundException(int brandId) {
        super("Brand ID " + brandId + " not found");
    }
}
