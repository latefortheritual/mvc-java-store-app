package org.example.framework;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.example.annotation.Log;
import org.example.exception.BadRequestException;
import org.example.exception.MethodNotAllowedException;
import org.example.exception.RouteNotFoundException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class DispatcherServlet extends HttpServlet {

    // convenția: verbul + prezența id-ului -> numele metodei din controller
    private static final Map<String, String> METHOD_NAMES = Map.of(
            "GET", "getAll",
            "GET {id}", "getOne",
            "POST", "create",
            "PUT {id}", "update",
            "PATCH {id}", "patch",
            "DELETE {id}", "delete"
    );

    // rețetele: cheia resursei -> cum se construiește controllerul; se completează doar la pornire
    private final Map<String, Supplier<Object>> factories = new HashMap<>();
    // instanțele deja create; Tomcat servește request-urile pe mai multe thread-uri
    private final Map<String, Object> instances = new ConcurrentHashMap<>();
    // un singur JsonMapper pentru toate controllerele
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public void register(String key, Supplier<Object> factory) {
        factories.put(key, factory);
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        String pathInfo = req.getPathInfo();// "/products/123"

        if(pathInfo == null || pathInfo.equals("/")){
            throw new RouteNotFoundException(req.getMethod(), req.getRequestURI());
        }

        String path = pathInfo.substring(1); // "products/123"

        // 1. cheia și id-ul: dacă ultimul segment e doar din cifre, e un id
        String key = path;
        String idText = null;
        int lastSlashIndex = path.lastIndexOf('/');
        String lastSegment = path.substring(lastSlashIndex + 1); // "123"
        if(lastSlashIndex != -1 && lastSegment.matches("[0-9]+")){
            idText = lastSegment;
            key = path.substring(0, lastSlashIndex); // "products"
        }

        // 2. controllerul, lazy: se creează la primul request care are nevoie de el, apoi e refolosit;
        // computeIfAbsent garantează o singură instanță chiar dacă două thread-uri ajung aici deodată
        Supplier<Object> factory = factories.get(key);
        if(factory == null){
            throw new RouteNotFoundException(req.getMethod(), req.getRequestURI());
        }
        Object controller = instances.computeIfAbsent(key, k -> factory.get());

        // 3. numele metodei, din convenție: "GET" sau "GET {id}"
        String conventionKey = req.getMethod();
        if(idText != null){
            conventionKey = conventionKey + " {id}";
        }
        String methodName = METHOD_NAMES.get(conventionKey); // "getOne"

        // 4. metoda, prin reflection; lipsește dacă verbul nu e în convenție sau controllerul nu o are
        Method method = findMethod(controller, methodName);
        if(method == null){
            throw new MethodNotAllowedException(req.getMethod(), req.getRequestURI());
        }

        // @Log e citită aici, de alt cod decât metoda adnotată
        if(method.isAnnotationPresent(Log.class)){
            // logger-ul are numele clasei controllerului, ca înainte
            LogManager.getLogger(controller.getClass()).info("{} {}", req.getMethod(), req.getRequestURI());
        }

        Object[] arguments = resolveArguments(method, idText, req);
        Object result = invoke(method, controller, arguments); // echivalentul lui controller.getOne(123)

        // 5. rezultatul devine JSON
        int status = 200;
        if(req.getMethod().equals("POST")){
            status = 201; // resursă nouă creată
        }
        resp.setStatus(status);
        resp.setContentType("application/json; charset=UTF-8");
        resp.getWriter().write(jsonMapper.writeValueAsString(result));
    }

    // metoda publică cu numele dat; null dacă nu există
    private Method findMethod(Object controller, String methodName) {
        if(methodName == null){
            return null;
        }
        for(Method method : controller.getClass().getMethods()){
            if(method.getName().equals(methodName)){
                return method;
            }
        }
        return null;
    }

    // tipul fiecărui parametru spune de unde vine valoarea lui
    private Object[] resolveArguments(Method method, String idText, HttpServletRequest req) throws IOException {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] arguments = new Object[parameterTypes.length];
        for(int i = 0; i < parameterTypes.length; i++){
            if(parameterTypes[i] == int.class){
                arguments[i] = parseId(idText); // id-ul din URL
            } else if(parameterTypes[i] == RequestParams.class){
                arguments[i] = new RequestParams(req.getParameterMap()); // query string-ul
            } else {
                // orice alt tip e body-ul JSON, de exemplu ProductWriteRequest
                arguments[i] = jsonMapper.readValue(req.getReader(), parameterTypes[i]);
            }
        }
        return arguments;
    }

    // validarea id-ului, o singură dată pentru toate resursele
    private int parseId(String idText) {
        // cel mult 9 cifre, ca să încapă sigur într-un int
        if(idText == null || !idText.matches("[0-9]{1,9}")){
            throw new BadRequestException("Id must be a positive integer");
        }
        int id = Integer.parseInt(idText);
        if(id <= 0){
            throw new BadRequestException("Id must be a positive integer");
        }
        return id;
    }

    private Object invoke(Method method, Object controller, Object[] arguments) {
        try {
            return method.invoke(controller, arguments);
        } catch (InvocationTargetException e) {
            // reflection învelește excepția aruncată de metodă; o despachetăm,
            // ca GlobalExceptionFilter să vadă de exemplu ProductNotFoundException
            Throwable cause = e.getCause();
            if(cause instanceof RuntimeException runtimeException){
                throw runtimeException;
            }
            if(cause instanceof Error error){
                throw error;
            }
            throw new IllegalStateException(cause);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
