package org.example.exception; // [router-1] stă lângă celelalte excepții, ca să extindă NotFoundException

public class RouteNotFoundException extends NotFoundException { // [router-1] fiind NotFoundException, GlobalExceptionFilter răspunde automat cu 404
    public RouteNotFoundException(String method, String uri) { // [router-1] primește verbul și URL-ul request-ului
        super("No route for " + method + " " + uri); // [router-1] mesajul din răspuns, de exemplu "No route for GET /brands"
    } // [router-1] sfârșitul constructorului
} // [router-1] sfârșitul clasei RouteNotFoundException
