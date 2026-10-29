package org.example.framework;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

public class PathInfoRequest extends HttpServletRequestWrapper {
    private final String pathInfo;

    public PathInfoRequest(HttpServletRequest request, String pathInfo){
        super(request);
        this.pathInfo = pathInfo;
    }

    @Override
    public String getPathInfo() {
        return getPathInfo();
    }
}
