package org.example.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.example.controller.MobileProductsController;
import org.example.controller.ProductController;
import org.example.controller.ProductsController;
import org.example.middleware.GlobalExceptionFilter;
import org.example.middleware.LoggingFilter;
import org.example.repository.BrandRepository;
import org.example.repository.CategoryRepository;
import org.example.repository.ProductRepository;
import org.example.service.ProductService;

import java.util.EnumSet;

@WebListener
public class AppInitializer implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Driverul JDBC PostgreSQL nu a putut fi incarcat", e
            );
        }

        DatabaseConfig databaseConfig = DatabaseConfig.fromEnvironment();

        ProductRepository productRepository = new ProductRepository(
                databaseConfig.getDbUrl(),
                databaseConfig.getDbUser(),
                databaseConfig.getDbPassword()
        );

        BrandRepository brandRepository = new BrandRepository(
                databaseConfig.getDbUrl(),
                databaseConfig.getDbUser(),
                databaseConfig.getDbPassword()
        );

        CategoryRepository categoryRepository = new CategoryRepository(
                databaseConfig.getDbUrl(),
                databaseConfig.getDbUser(),
                databaseConfig.getDbPassword()
        );

        ProductService productService = new ProductService(
                productRepository,
                brandRepository,
                categoryRepository
        );

        context.addServlet("productsServlet", new ProductsController(productService))
                .addMapping("/products");

        context.addServlet("productServlet", new ProductController(productService))
                .addMapping("/products/*");

        context.addServlet("mobileProductsServlet", new MobileProductsController(productService))
                .addMapping("/mobile/products");

        context.addFilter("loggingFilter", new LoggingFilter())
                .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");

        context.addFilter("globalExceptionFilter", new GlobalExceptionFilter())
                .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
    }
}