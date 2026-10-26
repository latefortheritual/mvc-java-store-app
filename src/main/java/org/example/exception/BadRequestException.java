package org.example.exception; // [router-5] stă lângă celelalte excepții tratate de GlobalExceptionFilter

public class BadRequestException extends RuntimeException { // [router-5] input invalid venit de la client, de exemplu un id care nu e număr
    public BadRequestException(String message) { // [router-5] mesajul ajunge în răspunsul JSON
        super(message); // [router-5] păstrează mesajul
    } // [router-5] sfârșitul constructorului
} // [router-5] sfârșitul clasei BadRequestException
