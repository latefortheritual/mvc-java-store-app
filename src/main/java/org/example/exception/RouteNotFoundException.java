package org.example.exception;

public class RouteNotFoundException extends NotFoundException {
    public RouteNotFoundException(String method, String uri) {
        super("No route for " + method + " " + uri);
    }
}
