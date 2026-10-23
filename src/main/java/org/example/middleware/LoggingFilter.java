package org.example.middleware;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class LoggingFilter implements Filter {

    // request -> filter1 ->filter2 ->servlet -> response
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        long start = System.currentTimeMillis();
        try{
            chain.doFilter(request, response); //passes the request on; servlet runs here
            // runs on the way out, after the servlet has finished
        } finally {
            long duration = System.currentTimeMillis() - start;
            System.out.println(
                    httpRequest.getMethod() + " "
                            + httpRequest.getRequestURI() + " "
                            + httpResponse.getStatus() + " "
                            + duration + "ms"
            );
        }
    }
}