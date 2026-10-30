package org.example.exception;

public class MethodNotAllowedException extends RuntimeException {
    public MethodNotAllowedException(String method, String uri) {
        super("Method " + method + " is not allowed on " + uri);
    }
}
