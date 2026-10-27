package org.example.framework; // [router-1] pachetul pentru codul de framework, separat de codul aplicației

import jakarta.servlet.ServletException; // [router-1] excepția pe care o poate arunca service()
import jakarta.servlet.http.HttpServlet; // [router-1] clasa de bază: dispatcher-ul este el însuși un servlet
import jakarta.servlet.http.HttpServletRequest; // [router-1] request-ul HTTP primit de la Tomcat
import jakarta.servlet.http.HttpServletResponse; // [router-1] răspunsul HTTP trimis înapoi clientului
import org.example.exception.BadRequestException; // [router-5] excepția pentru un id invalid în URL
import org.example.exception.MethodNotAllowedException; // [router-5] excepția pentru un verb pe care resursa nu îl suportă
import org.example.exception.RouteNotFoundException; // [router-1] excepția aruncată pentru o resursă necunoscută
import org.example.framework.annotation.Controller; // [router-6] adnotarea de pe clasă, cu path-ul de bază
import org.example.framework.annotation.HttpMethod; // [router-6] meta-adnotarea care spune ce verb reprezintă @Get, @Post și celelalte
import org.example.framework.annotation.PathVariable; // [router-6] adnotarea care leagă un parametru de o variabilă din șablon
import org.example.framework.annotation.ResponseStatus; // [router-6] adnotarea care schimbă statusul răspunsului reușit
import tools.jackson.databind.json.JsonMapper; // [router-5] dispatcher-ul citește și scrie JSON-ul în locul controllerelor

import java.io.IOException; // [router-1] excepția pe care o poate arunca service()
import java.lang.annotation.Annotation; // [router-6] tipul comun al tuturor adnotărilor, citite prin reflection
import java.lang.reflect.InvocationTargetException; // [router-5] învelișul în care reflection pune excepția aruncată de metoda apelată
import java.lang.reflect.Method; // [router-5] definiția unei metode, citită prin reflection
import java.lang.reflect.Parameter; // [router-6] definiția unui parametru, cu tot cu adnotările lui
import java.util.ArrayList; // [router-6] implementarea listei de rute
import java.util.HashMap; // [router-6] implementarea map-ului de variabile extrase din path
import java.util.List; // [router-6] tipul pentru lista de rute
import java.util.Map; // [router-2] tipul pentru registry și pentru cache-ul de instanțe

public class DispatcherServlet extends HttpServlet { // [router-1] front controller: singurul servlet care primește request-urile de produse

    private final Container container; // [router-3] containerul care creează controllerele și le injectează dependențele
    private final List<Route> routes = new ArrayList<>(); // [router-6] tabela de rute, construită o singură dată, la pornire, din adnotări
    private final JsonMapper jsonMapper = JsonMapper.builder().build(); // [router-5] un singur JsonMapper pentru toate controllerele

    public DispatcherServlet(Container container) { // [router-6] nu mai primește pachetul: rutele vin din adnotări, nu din convenția de nume
        this.container = container; // [router-3] păstrează containerul pentru request-uri
    } // [router-3] sfârșitul constructorului

    public void registerController(Class<?> controllerClass) { // [router-6] citește adnotările unei clase și îi adaugă rutele în tabelă
        Controller controller = controllerClass.getAnnotation(Controller.class); // [router-6] adnotarea de pe clasă, sau null dacă lipsește
        if (controller == null) { // [router-6] clasa nu e marcată ca controller
            throw new IllegalArgumentException(controllerClass.getName() + " is not annotated with @Controller"); // [router-6] greșeala apare la pornire, nu la primul request
        } // [router-6] sfârșitul verificării adnotării
        for (Method method : controllerClass.getMethods()) { // [router-6] parcurgem toate metodele publice
            for (Annotation annotation : method.getAnnotations()) { // [router-6] și toate adnotările fiecărei metode
                HttpMethod httpMethod = annotation.annotationType().getAnnotation(HttpMethod.class); // [router-6] căutăm meta-adnotarea pe tipul adnotării, de exemplu pe @Get
                if (httpMethod != null) { // [router-6] adnotarea este una de verb: @Get, @Post, @Put, @Patch sau @Delete
                    String template = controller.value() + readPath(annotation); // [router-6] path-ul complet: "/products" + "/{id}"
                    routes.add(new Route(httpMethod.value(), template.split("/", -1), controllerClass, method)); // [router-6] -1 păstrează segmentele goale, de exemplu cel din "/products/"
                } // [router-6] sfârșitul verificării de verb
            } // [router-6] sfârșitul buclei de adnotări
        } // [router-6] sfârșitul buclei de metode
    } // [router-6] sfârșitul metodei registerController

    private String readPath(Annotation annotation) { // [router-6] citește value() din orice adnotare de verb, fără să știm care este
        try { // [router-6] apelul prin reflection poate arunca excepții verificate
            return (String) annotation.annotationType().getMethod("value").invoke(annotation); // [router-6] atributele unei adnotări sunt metode, deci le apelăm ca pe orice metodă
        } catch (ReflectiveOperationException e) { // [router-6] adnotarea nu are o metodă value()
            throw new IllegalStateException(annotation.annotationType().getName() + " must declare String value()", e); // [router-6] greșeală în definiția adnotării
        } // [router-6] sfârșitul blocului try/catch
    } // [router-6] sfârșitul metodei readPath

    @Override // [router-1] suprascriem service, nu doGet: router-ul trebuie să primească orice verb
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException { // [router-1] Tomcat apelează metoda asta pentru fiecare request
        String path = request.getPathInfo(); // [router-1] cu maparea /*, aici vine tot path-ul, de exemplu "/products/123"
        if (path == null) { // [router-1] pentru URL-ul rădăcină, Tomcat poate întoarce null
            path = "/"; // [router-1] tratăm null ca rădăcina "/"
        } // [router-1] sfârșitul verificării de null
        String[] pathSegments = path.split("/", -1); // [router-6] "/products/123" devine ["", "products", "123"]

        boolean pathMatched = false; // [router-6] devine true dacă path-ul există, chiar dacă verbul nu se potrivește
        for (Route route : routes) { // [router-6] căutăm în tabela de rute
            Map<String, String> pathVariables = match(route.segments(), pathSegments); // [router-6] null dacă path-ul nu se potrivește cu șablonul
            if (pathVariables == null) { // [router-6] alt path
                continue; // [router-6] trecem la ruta următoare
            } // [router-6] sfârșitul verificării path-ului
            pathMatched = true; // [router-6] path-ul există în aplicație
            if (!route.httpMethod().equals(request.getMethod())) { // [router-6] path corect, dar alt verb
                continue; // [router-6] poate altă rută are același path și verbul cerut
            } // [router-6] sfârșitul verificării verbului
            handle(route, pathVariables, request, response); // [router-6] am găsit ruta: apelăm controllerul
            return; // [router-6] request-ul a fost tratat
        } // [router-6] sfârșitul căutării

        if (pathMatched) { // [router-6] path-ul există, dar niciun verb nu se potrivește
            throw new MethodNotAllowedException(request.getMethod(), request.getRequestURI()); // [router-6] GlobalExceptionFilter o transformă în 405
        } // [router-6] sfârșitul cazului 405
        throw new RouteNotFoundException(request.getMethod(), request.getRequestURI()); // [router-6] path-ul nu există deloc: GlobalExceptionFilter o transformă în 404
    } // [router-1] sfârșitul metodei service

    private Map<String, String> match(String[] templateSegments, String[] pathSegments) { // [router-6] potrivește un path cu un șablon și extrage variabilele
        if (templateSegments.length != pathSegments.length) { // [router-6] număr diferit de segmente, deci nu se pot potrivi
            return null; // [router-6] nu se potrivește
        } // [router-6] sfârșitul verificării lungimii
        Map<String, String> pathVariables = new HashMap<>(); // [router-6] aici strângem valorile, de exemplu id = "123"
        for (int i = 0; i < templateSegments.length; i++) { // [router-6] comparăm segment cu segment
            String templateSegment = templateSegments[i]; // [router-6] segmentul din șablon, de exemplu "products" sau "{id}"
            if (templateSegment.startsWith("{") && templateSegment.endsWith("}")) { // [router-6] segment variabil: acceptă orice valoare
                pathVariables.put(templateSegment.substring(1, templateSegment.length() - 1), pathSegments[i]); // [router-6] "{id}" devine cheia "id"
            } else if (!templateSegment.equals(pathSegments[i])) { // [router-6] segment fix care diferă
                return null; // [router-6] nu se potrivește
            } // [router-6] sfârșitul comparației
        } // [router-6] sfârșitul buclei de segmente
        return pathVariables; // [router-6] se potrivește; map-ul poate fi gol, de exemplu pentru "/products"
    } // [router-6] sfârșitul metodei match

    private void handle(Route route, Map<String, String> pathVariables, HttpServletRequest request, HttpServletResponse response) throws IOException { // [router-6] apelează controllerul și scrie răspunsul
        Object controller = container.get(route.controllerClass()); // [router-6] „dă-mi instanța și injectează tot”; tot lazy, la primul request
        Object[] arguments = resolveArguments(route.method(), pathVariables, request); // [router-6] din request construim valorile pentru parametrii metodei
        Object result = invoke(route.method(), controller, arguments); // [router-5] „apelează-mi în mod magic” metoda aleasă

        int status = 200; // [router-6] statusul implicit pentru un răspuns reușit
        ResponseStatus responseStatus = route.method().getAnnotation(ResponseStatus.class); // [router-6] metoda poate cere alt status
        if (responseStatus != null) { // [router-6] de exemplu create, cu @ResponseStatus(201)
            status = responseStatus.value(); // [router-6] folosim statusul cerut de adnotare
        } // [router-6] sfârșitul alegerii statusului
        response.setStatus(status); // [router-6] statusul vine acum din adnotare, nu dintr-un tabel din dispatcher
        response.setContentType("application/json; charset=UTF-8"); // [router-5] toate răspunsurile sunt JSON
        response.getWriter().write(jsonMapper.writeValueAsString(result)); // [router-5] obiectul întors de controller devine JSON
    } // [router-6] sfârșitul metodei handle

    private Object[] resolveArguments(Method method, Map<String, String> pathVariables, HttpServletRequest request) throws IOException { // [router-6] primește variabilele extrase din path, nu restul path-ului
        Parameter[] parameters = method.getParameters(); // [router-6] parametrii, cu tot cu adnotările lor
        Object[] arguments = new Object[parameters.length]; // [router-5] aici strângem valorile
        for (int i = 0; i < parameters.length; i++) { // [router-6] pentru fiecare parametru
            PathVariable pathVariable = parameters[i].getAnnotation(PathVariable.class); // [router-6] adnotarea @PathVariable, dacă există
            if (pathVariable != null) { // [router-6] parametrul vine din path
                arguments[i] = parseId(pathVariables.get(pathVariable.value())); // [router-6] valoarea variabilei cu numele din adnotare, de exemplu "id"
            } else if (parameters[i].getType() == RequestParams.class) { // [router-6] RequestParams înseamnă parametrii din query string
                arguments[i] = new RequestParams(request.getParameterMap()); // [router-5] controllerul primește parametrii, nu tot request-ul
            } else { // [router-5] orice alt tip este body-ul JSON
                arguments[i] = jsonMapper.readValue(request.getReader(), parameters[i].getType()); // [router-6] JSON-ul devine DTO-ul cerut, de exemplu ProductWriteRequest
            } // [router-5] sfârșitul alegerii sursei
        } // [router-5] sfârșitul buclei de parametri
        return arguments; // [router-5] valorile, în ordinea parametrilor
    } // [router-5] sfârșitul metodei resolveArguments

    private int parseId(String value) { // [router-6] primește valoarea deja extrasă din path, fără slash
        if (value == null || !value.matches("[0-9]{1,9}")) { // [router-6] doar cifre, cel mult 9, ca să încapă sigur într-un int; null dacă numele din @PathVariable nu există în șablon
            throw new BadRequestException("Id must be a positive integer"); // [router-5] mesaj corect, nu „Invalid request body”: rezolvă și punctul 4 din review
        } // [router-5] sfârșitul verificării formatului
        int id = Integer.parseInt(value); // [router-6] transformăm textul în număr
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

    private record Route(String httpMethod, String[] segments, Class<?> controllerClass, Method method) { // [router-6] o rută: verbul, șablonul împărțit în segmente, clasa și metoda controllerului
    } // [router-6] sfârșitul record-ului Route
} // [router-1] sfârșitul clasei DispatcherServlet
