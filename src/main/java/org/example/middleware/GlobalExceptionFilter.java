package org.example.middleware;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import org.example.dto.ErrorResponse;
import org.example.exception.BadRequestException; // [router-5] aruncată de dispatcher pentru un id invalid
import org.example.exception.DataAccessException;
import org.example.exception.MethodNotAllowedException; // [router-5] aruncată de dispatcher pentru un verb nesuportat
import org.example.exception.NotFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

public class GlobalExceptionFilter implements Filter {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        try {
            chain.doFilter(request, response);// the controller runs in here
        } catch (JacksonException e) {
            sendError(httpResponse, 400, "Bad Request", "Invalid request body");
        } catch (BadRequestException e) { // [router-5] input invalid în URL, de exemplu /products/abc
            sendError(httpResponse, 400, "Bad Request", e.getMessage()); // [router-5] mesajul explică exact ce e greșit
        } catch (MethodNotAllowedException e) { // [router-5] resursa există, dar nu suportă verbul
            sendError(httpResponse, 405, "Method Not Allowed", e.getMessage()); // [router-5] 405 în JSON, nu pagina HTML a lui Tomcat
        } catch (NotFoundException e) {
            sendError(httpResponse, 404, "Not Found", e.getMessage());
        } catch (DataAccessException e) {
            e.printStackTrace();
            sendError(httpResponse, 503, "Service Unavailable", "Database unavailable");
        } catch (RuntimeException e) {
            e.printStackTrace();
            sendError(httpResponse, 500, "Internal Server Error", "An unexpected error occurred");
        }
    }

    private void sendError(HttpServletResponse response, int statusCode, String error, String message) throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json; charset=UTF-8");
        ErrorResponse errorResponse = new ErrorResponse(statusCode, error, message);
        response.getWriter().write(jsonMapper.writeValueAsString(errorResponse));
    }
}
