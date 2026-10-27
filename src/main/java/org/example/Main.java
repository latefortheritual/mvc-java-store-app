package org.example;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.example.config.DatabaseConfig;
import org.example.controller.MobileProductsController;
import org.example.controller.ProductsController; // [router-6] Main listează explicit controllerele, ca în varianta 6a
import org.example.framework.Container; // [router-3] containerul de dependency injection
import org.example.framework.DispatcherServlet; // [router-1] front controller-ul care primește request-urile de produse
import org.example.middleware.GlobalExceptionFilter;
import org.example.middleware.LoggingFilter;

public class Main {
    static void main() throws LifecycleException {

        Tomcat tomcat = new Tomcat();

        tomcat.setPort(8083);
        tomcat.getConnector();

        Context context = tomcat.addContext("", null);


        Container container = new Container(); // [router-3] containerul care construiește tot lanțul de dependențe
        container.register(DatabaseConfig.class, DatabaseConfig.fromEnvironment()); // [router-3] singurul obiect dat manual: vine din variabilele de mediu, nu dintr-un constructor

        DispatcherServlet dispatcherServlet = new DispatcherServlet(container); // [router-6] fără pachet: rutele vin din adnotări, nu din convenția de nume
        dispatcherServlet.registerController(ProductsController.class); // [router-6] citește @Controller și adnotările de verb, la pornire
        dispatcherServlet.registerController(MobileProductsController.class); // [router-6] ruta de mobile intră și ea în router

        Tomcat.addServlet(context, "dispatcherServlet", dispatcherServlet); // [router-6] singurul servlet al aplicației
        context.addServletMappingDecoded("/*", "dispatcherServlet"); // [router-1] cu /*, tot path-ul ajunge în getPathInfo(); cu / ar fi ajuns în getServletPath()

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