package org.example.framework; // [router-1] pachetul pentru codul de framework, separat de codul aplicației

import jakarta.servlet.ServletException; // [router-1] excepția pe care o poate arunca service()
import jakarta.servlet.http.HttpServlet; // [router-1] clasa de bază: dispatcher-ul este el însuși un servlet
import jakarta.servlet.http.HttpServletRequest; // [router-1] request-ul HTTP primit de la Tomcat
import jakarta.servlet.http.HttpServletResponse; // [router-1] răspunsul HTTP trimis înapoi clientului
import org.example.exception.BadRequestException; // [router-5] excepția pentru un id invalid în URL
import org.example.exception.MethodNotAllowedException; // [router-5] excepția pentru un verb pe care resursa nu îl suportă
import org.example.exception.RouteNotFoundException; // [router-1] excepția aruncată pentru o resursă necunoscută
import tools.jackson.databind.json.JsonMapper; // [router-5] dispatcher-ul citește și scrie JSON-ul în locul controllerelor

import java.io.IOException; // [router-1] excepția pe care o poate arunca service()
import java.lang.reflect.InvocationTargetException; // [router-5] învelișul în care reflection pune excepția aruncată de metoda apelată
import java.lang.reflect.Method; // [router-5] definiția unei metode, citită prin reflection
import java.util.Map; // [router-2] tipul pentru registry și pentru cache-ul de instanțe
import java.util.concurrent.ConcurrentHashMap; // [router-4] map sigur la scrieri în paralel: convenția adaugă rute în timpul request-urilor

public class DispatcherServlet extends HttpServlet { // [router-1] front controller: singurul servlet care primește request-urile de produse

    private static final Map<String, String> ACTIONS = Map.of( // [router-5] tabelul de la decizia 6: verbul și forma rutei aleg metoda controllerului
            "GET", "getAll", // [router-5] GET /products
            "GET/{id}", "getOne", // [router-5] GET /products/123
            "POST", "create", // [router-5] POST /products
            "PUT/{id}", "update", // [router-5] PUT /products/123
            "PATCH/{id}", "patch", // [router-5] PATCH /products/123
            "DELETE/{id}", "delete" // [router-5] DELETE /products/123
    ); // [router-5] sfârșitul tabelului de acțiuni
    private static final Map<String, Integer> STATUS_CODES = Map.of("create", 201); // [router-5] create răspunde cu 201 Created; toate celelalte metode cu 200

    private final Container container; // [router-3] containerul care creează controllerele și le injectează dependențele
    private final String controllerPackage; // [router-4] pachetul în care convenția caută controllerele, de exemplu "org.example.controller"
    private final Map<String, Class<?>> routes = new ConcurrentHashMap<>(); // [router-5] cache-ul convenției: resursa indică spre clasa controllerului, care nu mai e un servlet
    private final JsonMapper jsonMapper = JsonMapper.builder().build(); // [router-5] un singur JsonMapper pentru toate controllerele

    public DispatcherServlet(Container container, String controllerPackage) { // [router-4] pachetul vine din Main, ca framework-ul să nu știe singur nimic despre aplicație
        this.container = container; // [router-3] păstrează containerul pentru request-uri
        this.controllerPackage = controllerPackage; // [router-4] păstrează pachetul pentru convenție
    } // [router-3] sfârșitul constructorului

    @Override // [router-1] suprascriem service, nu doGet: router-ul trebuie să primească orice verb
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException { // [router-1] Tomcat apelează metoda asta pentru fiecare request
        String path = request.getPathInfo(); // [router-1] cu maparea /*, aici vine tot path-ul, de exemplu "/products/123"
        if (path == null) { // [router-1] pentru URL-ul rădăcină, Tomcat poate întoarce null
            path = "/"; // [router-1] tratăm null ca rădăcina "/"
        } // [router-1] sfârșitul verificării de null

        String pathWithoutSlash = path.substring(1); // [router-1] scoatem slash-ul de la început: "products/123"
        int slashIndex = pathWithoutSlash.indexOf('/'); // [router-1] poziția slash-ului dintre resursă și rest, sau -1 dacă nu există

        String resource; // [router-1] primul segment din path, de exemplu "products"
        String rest; // [router-1] ce urmează după resursă, de exemplu "/123", sau "" dacă nu urmează nimic
        if (slashIndex == -1) { // [router-1] nu există alt slash: path-ul conține doar resursa
            resource = pathWithoutSlash; // [router-1] tot path-ul este resursa: "products"
            rest = ""; // [router-1] nu există id
        } else { // [router-1] există slash: după resursă urmează ceva
            resource = pathWithoutSlash.substring(0, slashIndex); // [router-1] partea dinaintea slash-ului: "products"
            rest = pathWithoutSlash.substring(slashIndex); // [router-1] partea de la slash încolo: "/123"
        } // [router-1] sfârșitul separării path-ului

        Class<?> controllerClass = routes.get(resource); // [router-5] un singur controller per resursă, deci cheia este doar resursa
        if (controllerClass == null) { // [router-4] nu există: încercăm convenția de nume
            controllerClass = findControllerByConvention(resource); // [router-4] "products" devine clasa ProductsController
            if (controllerClass != null) { // [router-4] convenția a găsit o clasă
                routes.put(resource, controllerClass); // [router-5] o păstrăm sub numele resursei, ca la următorul request să nu o mai căutăm
            } // [router-4] sfârșitul salvării în cache
        } // [router-4] sfârșitul căutării prin convenție
        if (controllerClass == null) { // [router-5] convenția nu a găsit un controller
            throw new RouteNotFoundException(request.getMethod(), request.getRequestURI()); // [router-1] GlobalExceptionFilter o transformă în 404
        } // [router-2] sfârșitul verificării rutei

        String actionKey; // [router-5] cheia din tabelul de acțiuni
        if (rest.isEmpty()) { // [router-5] fără id: acțiune pe colecție
            actionKey = request.getMethod(); // [router-5] de exemplu "GET"
        } else { // [router-5] cu id: acțiune pe un element
            actionKey = request.getMethod() + "/{id}"; // [router-5] de exemplu "GET/{id}"
        } // [router-5] sfârșitul alegerii cheii
        String methodName = ACTIONS.get(actionKey); // [router-5] de exemplu "getOne"
        if (methodName == null) { // [router-5] combinația nu e în tabel, de exemplu DELETE /products
            throw new MethodNotAllowedException(request.getMethod(), request.getRequestURI()); // [router-5] GlobalExceptionFilter o transformă în 405
        } // [router-5] sfârșitul verificării acțiunii
        Method method = findMethod(controllerClass, methodName); // [router-5] căutăm metoda pe clasa controllerului, prin reflection
        if (method == null) { // [router-5] controllerul nu are metoda, deci nu suportă acțiunea
            throw new MethodNotAllowedException(request.getMethod(), request.getRequestURI()); // [router-5] tot 405
        } // [router-5] sfârșitul verificării metodei

        Object controller = container.get(controllerClass); // [router-5] „dă-mi instanța și injectează tot”; controllerul e acum o clasă simplă
        Object[] arguments = resolveArguments(method, request, rest); // [router-5] din request construim valorile pentru parametrii metodei
        Object result = invoke(method, controller, arguments); // [router-5] „apelează-mi în mod magic” metoda aleasă

        response.setStatus(STATUS_CODES.getOrDefault(methodName, 200)); // [router-5] 201 pentru create, 200 pentru restul
        response.setContentType("application/json; charset=UTF-8"); // [router-5] toate răspunsurile sunt JSON
        response.getWriter().write(jsonMapper.writeValueAsString(result)); // [router-5] obiectul întors de controller devine JSON
    } // [router-1] sfârșitul metodei service

    private Method findMethod(Class<?> controllerClass, String methodName) { // [router-5] caută o metodă publică după nume
        for (Method method : controllerClass.getMethods()) { // [router-5] definiția clasei ne dă lista metodelor publice
            if (method.getName().equals(methodName)) { // [router-5] am găsit metoda cu numele din tabel
                return method; // [router-5] o întoarcem
            } // [router-5] sfârșitul comparației
        } // [router-5] sfârșitul buclei
        return null; // [router-5] clasa nu are o metodă cu numele ăsta
    } // [router-5] sfârșitul metodei findMethod

    private Object[] resolveArguments(Method method, HttpServletRequest request, String rest) throws IOException { // [router-5] echivalentul argument resolver-ilor din Spring
        Class<?>[] parameterTypes = method.getParameterTypes(); // [router-5] tipurile parametrilor spun ce valori vrea metoda
        Object[] arguments = new Object[parameterTypes.length]; // [router-5] aici strângem valorile
        for (int i = 0; i < parameterTypes.length; i++) { // [router-5] pentru fiecare parametru
            Class<?> parameterType = parameterTypes[i]; // [router-5] tipul parametrului curent
            if (parameterType == int.class) { // [router-5] un int este id-ul din URL
                arguments[i] = parseId(rest); // [router-5] "/123" devine 123
            } else if (parameterType == RequestParams.class) { // [router-5] RequestParams înseamnă parametrii din query string
                arguments[i] = new RequestParams(request.getParameterMap()); // [router-5] controllerul primește parametrii, nu tot request-ul
            } else { // [router-5] orice alt tip este body-ul JSON
                arguments[i] = jsonMapper.readValue(request.getReader(), parameterType); // [router-5] JSON-ul devine DTO-ul cerut, de exemplu ProductWriteRequest
            } // [router-5] sfârșitul alegerii sursei
        } // [router-5] sfârșitul buclei de parametri
        return arguments; // [router-5] valorile, în ordinea parametrilor
    } // [router-5] sfârșitul metodei resolveArguments

    private int parseId(String rest) { // [router-5] validarea id-ului, mutată din controller: o singură dată pentru toate resursele
        if (!rest.matches("/[0-9]{1,9}")) { // [router-5] exact un segment, doar cifre, cel mult 9, ca să încapă sigur într-un int
            throw new BadRequestException("Id must be a positive integer"); // [router-5] mesaj corect, nu „Invalid request body”: rezolvă și punctul 4 din review
        } // [router-5] sfârșitul verificării formatului
        int id = Integer.parseInt(rest.substring(1)); // [router-5] scoatem slash-ul și transformăm textul în număr
        if (id <= 0) { // [router-5] zero nu e un id valid
            throw new BadRequestException("Id must be a positive integer"); // [router-5] același mesaj
        } // [router-5] sfârșitul verificării valorii
        return id; // [router-5] id-ul valid
    } // [router-5] sfârșitul metodei parseId

    private Object invoke(Method method, Object controller, Object[] arguments) { // [router-5] apelul dinamic, plus despachetarea excepțiilor
        try { // [router-5] reflection poate arunca excepții verificate
            return method.invoke(controller, arguments); // [router-5] echivalentul lui controller.getOne(123), apelat dinamic
        } catch (InvocationTargetException e) { // [router-5] metoda a aruncat o excepție, iar reflection a învelit-o
            Throwable cause = e.getCause(); // [router-5] excepția originală, de exemplu ProductNotFoundException
            if (cause instanceof RuntimeException runtimeException) { // [router-5] cazul obișnuit
                throw runtimeException; // [router-5] o aruncăm mai departe neschimbată, ca GlobalExceptionFilter să o recunoască
            } // [router-5] sfârșitul cazului RuntimeException
            if (cause instanceof Error error) { // [router-5] erori grave ale JVM-ului
                throw error; // [router-5] le aruncăm mai departe neschimbate
            } // [router-5] sfârșitul cazului Error
            throw new IllegalStateException(cause); // [router-5] o excepție verificată: o învelim într-una runtime
        } catch (IllegalAccessException e) { // [router-5] metoda nu poate fi apelată, de exemplu nu e publică
            throw new IllegalStateException(e); // [router-5] e o greșeală de programare, deci 500
        } // [router-5] sfârșitul blocului try/catch
    } // [router-5] sfârșitul metodei invoke

    private Class<?> findControllerByConvention(String resource) { // [router-5] convenția: numele resursei din URL, capitalizat, plus sufixul Controller
        if (!resource.matches("[a-z]+")) { // [router-4] doar litere mici: altfel URL-ul ar putea alege clase neintenționate, de exemplu cu "." sau "$"
            return null; // [router-4] nume invalid, deci nicio clasă
        } // [router-4] sfârșitul validării numelui

        String className = String.format("%s.%sController", controllerPackage, capitalize(resource)); // [router-4] placeholder-ele din note: "org.example.controller" + "." + "Products" + "Controller"
        try { // [router-4] Class.forName aruncă excepție dacă nu există o clasă cu numele dat
            return Class.forName(className); // [router-5] orice clasă cu numele potrivit e controller; nu mai trebuie să fie servlet
        } catch (ClassNotFoundException | NoClassDefFoundError e) { // [router-4] nu există clasa; pe macOS, "Mobileproducts" găsește fișierul MobileProductsController.class, iar JVM-ul îl respinge cu NoClassDefFoundError
            return null; // [router-4] resursa nu are controller
        } // [router-4] sfârșitul încărcării clasei
    } // [router-4] sfârșitul metodei findControllerByConvention

    private String capitalize(String text) { // [router-4] capitalize din note: prima literă devine majusculă
        return Character.toUpperCase(text.charAt(0)) + text.substring(1); // [router-4] "products" devine "Products"
    } // [router-4] sfârșitul metodei capitalize
} // [router-1] sfârșitul clasei DispatcherServlet
