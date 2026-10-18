package org.example;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.example.config.DatabaseConfig;
import org.example.controller.ProductController;
import org.example.middleware.LoggingFilter;
import org.example.repository.BrandRepository;
import org.example.repository.CategoryRepository;
import org.example.repository.ProductRepository;
import org.example.service.ProductService;

public class Main {
    static void main() throws LifecycleException {

        Tomcat tomcat = new Tomcat();

        tomcat.setPort(8083);

        // opens port 8083, accepts incoming connections, reads the raw http text Bruno sends and
        // turns it into the request object your code will receive later
        tomcat.getConnector();

        //add an empty context = the space where your app's servlets will live later
        // servlet = a java class that handles HTTP requests inside Tomcat
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

        // pune obiectul TicketController in container sub numele "ticketServlet"
        Tomcat.addServlet(context, "productServlet", new ProductController(productService));
        // TicketController este un servlet ptr Tomcat (extends HttpServlet) si are si rol de controller
        // servlet = clasa Java care raspunde la cereri HTTP + ruleaza in interiorul unui server (Tomcat)

        context.addServletMappingDecoded("/product", "productServlet");

        //filters take 2 objects: a definition(the filter and its name) and a mapping (which paths it applies to)
        FilterDef loggingDef = new FilterDef();
        loggingDef.setFilterName("loggingFilter");
        loggingDef.setFilter(new LoggingFilter());
        context.addFilterDef(loggingDef);

        FilterMap loggingMap = new FilterMap();
        loggingMap.setFilterName("loggingFilter");
        loggingMap.addURLPattern("/*");
        context.addFilterMap(loggingMap);

        //start it
        tomcat.start();

        //keep the program running; without this main can finish right after starting and the server stops with it
        tomcat.getServer().await();
    }
}