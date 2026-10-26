package org.example.exception; // [router-5] stă lângă celelalte excepții tratate de GlobalExceptionFilter

public class MethodNotAllowedException extends RuntimeException { // [router-5] resursa există, dar nu suportă verbul cerut
    public MethodNotAllowedException(String method, String uri) { // [router-5] primește verbul și URL-ul request-ului
        super("Method " + method + " is not allowed on " + uri); // [router-5] de exemplu "Method DELETE is not allowed on /products"
    } // [router-5] sfârșitul constructorului
} // [router-5] sfârșitul clasei MethodNotAllowedException
