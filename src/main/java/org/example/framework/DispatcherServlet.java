package org.example.framework;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.controller.ProductController;
import org.example.controller.ProductsController;
import org.example.exception.RouteNotFoundException;
import java.io.IOException;

public class DispatcherServlet extends HttpServlet {

    private final ProductController productController;
    private final ProductsController productsController;

    public DispatcherServlet(ProductController productController, ProductsController productsController) {
        this.productController = productController;
        this.productsController = productsController;
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        String pathInfo = req.getPathInfo();// "/products/123"

        if(pathInfo == null || pathInfo.equals("/")){
            throw new RouteNotFoundException(req.getMethod(), req.getRequestURI());
        }

        String pathWithoutSlash = pathInfo.substring(1); //"products/123"

        int slashIndex = pathWithoutSlash.indexOf('/');
        String resource;
        String remainingPath;
        if(slashIndex == -1){
            resource = pathWithoutSlash;
            remainingPath = "";
        }else{
            resource = pathWithoutSlash.substring(0, slashIndex); // "products"
            remainingPath = pathWithoutSlash.substring(slashIndex);      // "/123" sau /abc sau /123/extra
        }

        if(resource.equals("products") && remainingPath.isEmpty()){
            productsController.service(req,resp);
        } else if (resource.equals("products") && !remainingPath.isEmpty()){
            PathInfoRequest adaptedRequest = new PathInfoRequest(req,remainingPath);
            productController.service(adaptedRequest, resp);
        } else {
            throw new RouteNotFoundException(req.getMethod(), req.getRequestURI());
        }
    }
}