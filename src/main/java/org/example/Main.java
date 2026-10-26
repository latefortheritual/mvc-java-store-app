package org.example;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.example.config.DatabaseConfig;
import org.example.controller.MobileProductsController;
import org.example.controller.ProductController;
import org.example.controller.ProductsController;
import org.example.middleware.GlobalExceptionFilter;
import org.example.middleware.LoggingFilter;
import org.example.repository.BrandRepository;
import org.example.repository.CategoryRepository;
import org.example.repository.ProductRepository;
import org.example.service.ProductService;

public class Main {
    static void main() throws LifecycleException {

        Tomcat tomcat = new Tomcat();

        tomcat.setPort(8083);
        tomcat.getConnector();

        Context context = tomcat.addContext("", null);


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

        ProductService productService = new ProductService(productRepository, brandRepository, categoryRepository);


        Tomcat.addServlet(context, "productsServlet", new ProductsController(productService));
        Tomcat.addServlet(context, "productServlet", new ProductController(productService));
        Tomcat.addServlet(context, "mobileProductsServlet", new MobileProductsController(productService));

        context.addServletMappingDecoded("/products", "productsServlet");
        context.addServletMappingDecoded("/products/*", "productServlet");
        context.addServletMappingDecoded("/mobile/products", "mobileProductsServlet");

        //filters take 2 objects: a definition(the filter and its name) and a mapping (which paths it applies to)
        FilterDef loggingDef = new FilterDef();
        loggingDef.setFilterName("loggingFilter");
        loggingDef.setFilter(new LoggingFilter());
        context.addFilterDef(loggingDef);

        FilterMap loggingMap = new FilterMap();
        loggingMap.setFilterName("loggingFilter");
        loggingMap.addURLPattern("/*");
        context.addFilterMap(loggingMap);

        FilterDef exceptionDef = new FilterDef();
        exceptionDef.setFilterName("globalExceptionFilter");
        exceptionDef.setFilter(new GlobalExceptionFilter());
        context.addFilterDef(exceptionDef);

        FilterMap exceptionMap = new FilterMap();
        exceptionMap.setFilterName("globalExceptionFilter");
        exceptionMap.addURLPattern("/*");
        context.addFilterMap(exceptionMap);

        tomcat.start();

        //keep the program running; without this main can finish right after starting and the server stops with it
        tomcat.getServer().await();
    }
}