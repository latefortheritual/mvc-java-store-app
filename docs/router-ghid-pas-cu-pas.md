# Ghid pas cu pas: construiește-ți propriul router în Java

Ghidul te duce de la o aplicație cu servleturi mapate manual la un mini-framework MVC propriu. La final vei avea un router care pornește de la URL, un container de dependency injection, controllere care nu mai știu nimic despre HTTP și rute declarate cu adnotări proprii. Pe drum vei înțelege ce face Spring în spatele adnotărilor.

Pașii 1 până la 5 se termină în commit-ul `6fb488f`. Pasul 6 adaugă adnotările peste el.

Cerința originală, deciziile luate și motivele lor sunt în `docs/router-cerinta.md`. Citește-l înainte, sau măcar secțiunea „Decizii luate”.

## Cuprins

0. Pregătirea
1. Front controller manual
2. Registry cu instanțe create lazy
3. Containerul de dependency injection
4. Convenția de nume
5. Controller propriu, detașat de HttpServlet
6. Rute declarate cu adnotări
7. Capcane întâlnite
8. Exerciții

## 0. Pregătirea

### Punctul de plecare

Pornești de la commit-ul `61025b2`, „Validate brand and category on PUT and PATCH”. Asta e starea aplicației înainte de router.

```
git checkout -b my-router 61025b2
```

Uită-te la `Main.java` înainte să începi. Vei vedea două lucruri pe care le vei elimina pe parcurs:

- **Tomcat face rutarea.** Trei URL-uri sunt mapate manual la trei servleturi: `/products`, `/products/*` și `/mobile/products`.
- **Main construiește manual tot lanțul de dependențe:** configurația, cele trei repository-uri, serviciul și controllerele.

Uită-te și la cele două controllere de produse. `ProductsController` servește colecția, iar `ProductController` servește un singur produs. Fiecare extinde `HttpServlet`, parsează singur id-ul și scrie singur JSON-ul. Codul acesta duplicat va dispărea la pasul 5.

### Ce îți trebuie

- **JDK 25.** Pom-ul compilează pentru Java 25, iar `Main` folosește forma `static void main()` fără parametri. Verifică cu `mvn -v` că Maven chiar rulează pe 25. Dacă vezi `invalid target release: 25`, variabila `JAVA_HOME` indică spre un JDK mai vechi.
- **PostgreSQL cu schema și datele de test.** Fișierele sunt în folderul `database`:
  ```
  psql -d store -f database/schema.sql
  psql -d store -f database/seed_brand.sql
  psql -d store -f database/seed_category.sql
  psql -d store -f database/seed_product.sql
  ```
- **Trei variabile de mediu** pentru conexiune: `DB_URL`, `DB_USER` și `DB_PASSWORD`. Fără ele, aplicația se oprește la pornire cu un mesaj clar.

### Cum pornești aplicația

Cel mai simplu e din IDE: rulezi `Main`, cu cele trei variabile de mediu setate în configurația de rulare. Din terminal merge așa:

```
mvn -q compile dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp target/classes:$(cat cp.txt) org.example.Main
```

Aplicația ascultă pe portul 8083.

### Cum verifici după fiecare pas

Fiecare pas se termină cu aceeași verificare, în trei părți:

1. **Compilarea:**
   ```
   mvn -q compile test-compile
   ```
2. **Testele E2E,** cu aplicația pornită. Ele apelează API-ul real pe portul 8083:
   ```
   mvn test -Dtest=ProductsE2ETest
   ```
3. **Câteva request-uri manuale.** Folosește valorile actuale ale produsului 1, ca să nu modifici datele:
   ```
   curl -i localhost:8083/products/1
   curl -i "localhost:8083/products?page=1&size=2"
   curl -i -X PATCH localhost:8083/products/1 -H "Content-Type: application/json" -d '{"price":305.95}'
   curl -i localhost:8083/products/abc
   curl -i -X DELETE localhost:8083/products
   curl -i localhost:8083/mobile/products
   curl -i localhost:8083/brands
   ```

**Regula de aur: API-ul trebuie să se comporte la fel după fiecare pas.** Schimbăm mecanismul, nu comportamentul. Excepțiile sunt puține și sunt anunțate explicit în pasul respectiv.

**Repornește aplicația după fiecare pas.** Altfel testezi codul vechi.

## 1. Front controller manual

### Scopul

Un singur servlet primește toate request-urile de produse și decide singur unde le trimite. Decizia o ia deocamdată printr-un if-else scris de mână.

### Conceptul

Acest servlet se numește **front controller**. În Spring se numește `DispatcherServlet`. Ideea-cheie a pasului este metoda `service(request, response)` din `HttpServlet`. Tomcat o apelează pentru fiecare request, iar implementarea ei din `HttpServlet` citește verbul și apelează `doGet`, `doPost` și celelalte.

Asta înseamnă că „magia” de a ajunge de la verb la metodă există deja. Dacă apelezi `service()` pe un servlet, el alege singur metoda potrivită. Front controller-ul nostru trebuie doar să aleagă servletul.

Observă că și `HttpServlet.service` alege metoda tot printr-un șir de if-uri. Framework-urile nu elimină decizia. O mută într-un singur loc, generic.

### Ce modifici, în ordine

1. Creezi pachetul `org.example.framework`. Aici stă tot codul de framework, separat de aplicație. Motivul e explicat în `docs/router-cerinta.md`, la „De ce un pachet framework”.
2. Creezi `RouteNotFoundException` în pachetul `exception`.
3. Creezi `DispatcherServlet` în pachetul `framework`.
4. Modifici `Main`.

### Pasul 1.1: `RouteNotFoundException`

<!-- verify:file step=1 path=src/main/java/org/example/exception/RouteNotFoundException.java -->
```java
package org.example.exception;

public class RouteNotFoundException extends NotFoundException {
    public RouteNotFoundException(String method, String uri) {
        super("No route for " + method + " " + uri);
    }
}
```

**De ce extinde `NotFoundException`.** `GlobalExceptionFilter` prinde deja orice `NotFoundException` și răspunde cu 404 în JSON. Excepția nouă primește comportamentul ăsta gratis, fără nicio modificare în filtru. E un model pe care îl vei refolosi: codul semnalează problema printr-o excepție, iar filtrul global alege statusul HTTP.

### Pasul 1.2: `DispatcherServlet`

<!-- verify:file step=1 path=src/main/java/org/example/framework/DispatcherServlet.java -->
```java
package org.example.framework;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.example.exception.RouteNotFoundException;

import java.io.IOException;

public class DispatcherServlet extends HttpServlet {

    private final HttpServlet productsServlet;
    private final HttpServlet productServlet;

    public DispatcherServlet(HttpServlet productsServlet, HttpServlet productServlet) {
        this.productsServlet = productsServlet;
        this.productServlet = productServlet;
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String path = request.getPathInfo(); // "/products/123"
        if (path == null) {
            path = "/";
        }

        String pathWithoutSlash = path.substring(1); // "products/123"
        int slashIndex = pathWithoutSlash.indexOf('/');

        String resource; // "products"
        String rest;     // "/123", sau "" dacă nu există
        if (slashIndex == -1) {
            resource = pathWithoutSlash;
            rest = "";
        } else {
            resource = pathWithoutSlash.substring(0, slashIndex);
            rest = pathWithoutSlash.substring(slashIndex);
        }

        if (resource.equals("products") && rest.isEmpty()) {
            productsServlet.service(request, response);
        } else if (resource.equals("products")) {
            productServlet.service(new PathInfoRequest(request, rest), response);
        } else {
            throw new RouteNotFoundException(request.getMethod(), request.getRequestURI());
        }
    }

    private static class PathInfoRequest extends HttpServletRequestWrapper {
        private final String pathInfo;

        PathInfoRequest(HttpServletRequest request, String pathInfo) {
            super(request);
            this.pathInfo = pathInfo;
        }

        @Override
        public String getPathInfo() {
            return pathInfo;
        }
    }
}
```

**De ce suprascrii `service`, nu `doGet`.** Router-ul trebuie să primească orice verb. `service` e prima metodă apelată de Tomcat, înainte de orice alegere după verb.

**De ce împarți path-ul în resursă și rest.** Din `/products/123` rezultă resursa `products` și restul `/123`. Resursa decide controllerul, iar restul conține id-ul.

**De ce ai nevoie de `PathInfoRequest`.** Fără router, Tomcat îi dădea lui `ProductController` id-ul în `getPathInfo()` sub forma `/123`. Cu router-ul mapat pe tot, `getPathInfo()` întoarce `/products/123`, iar validarea din controller ar respinge fiecare request. Wrapper-ul e un request care deleagă totul către cel original, cu o singură excepție: întoarce `/123` la `getPathInfo()`. Așa nu trebuie să modifici controllerul. Clasa e `private static`, fiindcă nu o folosește nimeni altcineva.

**If-else-ul e intenționat.** E varianta manuală, punctul de plecare. Pasul 2 îl înlocuiește.

### Pasul 1.3: `Main`

**Importurile.** Adaugi `DispatcherServlet`. Blocul de importuri arată acum așa:

<!-- verify:main-imports step=1 -->
```java
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.example.config.DatabaseConfig;
import org.example.controller.MobileProductsController;
import org.example.controller.ProductController;
import org.example.controller.ProductsController;
import org.example.framework.DispatcherServlet;
import org.example.middleware.GlobalExceptionFilter;
import org.example.middleware.LoggingFilter;
import org.example.repository.BrandRepository;
import org.example.repository.CategoryRepository;
import org.example.repository.ProductRepository;
import org.example.service.ProductService;
```

**Înregistrarea servleturilor.** Înlocuiești tot ce se află între linia cu `tomcat.addContext("", null)` și comentariul `//filters take 2 objects`:

<!-- verify:main-wiring step=1 -->
```java
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

        ProductsController productsController = new ProductsController(productService);
        ProductController productController = new ProductController(productService);

        Tomcat.addServlet(context, "dispatcherServlet", new DispatcherServlet(productsController, productController));
        Tomcat.addServlet(context, "mobileProductsServlet", new MobileProductsController(productService));

        context.addServletMappingDecoded("/*", "dispatcherServlet");
        context.addServletMappingDecoded("/mobile/products", "mobileProductsServlet");
```

Partea de sus, cu configurația, repository-urile și serviciul, rămâne neschimbată. Se schimbă doar ultimele șase linii.

**De ce `/*` și nu `/`.** Cu `/*`, Tomcat pune tot path-ul în `getPathInfo()`. Cu `/`, path-ul ar ajunge în `getServletPath()`, iar `getPathInfo()` ar fi `null`.

**De ce `/mobile/products` merge în continuare.** În Tomcat, o mapare exactă câștigă întotdeauna în fața lui `/*`. Ruta de mobile ocolește deci dispatcher-ul și ajunge direct la servletul ei.

**Filtrele rămân neschimbate.** Ele se aplică înaintea oricărui servlet.

### Verificarea

Rulează verificarea de la pasul 0. Toate răspunsurile trebuie să fie ca înainte, cu o singură diferență: `/brands` întoarce acum un 404 în JSON, cu mesajul „No route for GET /brands”. Înainte, Tomcat întorcea o pagină HTML. E o îmbunătățire.

### Corespondentul în Spring

`DispatcherServlet`, mapat implicit pe toată aplicația.

## 2. Registry cu instanțe create lazy

### Scopul

Dispatcher-ul nu mai conține string-ul „products” și nici nu mai știe de clasele de controller. Main înregistrează rutele, iar controllerele se creează abia la primul request.

### Conceptul

Notele inițiale spuneau: „ahead of time o să ai o listă în care vei ține că Products indică spre new ProductsController”. Lista aceasta este un **registry**, iar modelul se numește **Factory**: cineva știe să creeze obiecte la cerere.

În Java, rețeta „știu să creez un obiect, dar nu îl creez încă” se scrie cu un `Supplier`. Un lambda ca `() -> new ProductsController(productService)` este o astfel de rețetă. Ea rulează doar când cineva apelează `get()`. Asta se numește **lazy loading**.

**Cheia rutei.** Resursa `products` are încă două servleturi, unul pentru colecție și unul pentru element. Ca registry-ul să le poată distinge, cheia folosește un șablon: `products` pentru colecție și `products/{id}` pentru element. E aceeași idee ca la `@GetMapping("/{id}")` din Spring.

### Ce modifici, în ordine

1. `DispatcherServlet`
2. `Main`

### Pasul 2.1: `DispatcherServlet`

<!-- verify:file step=2 path=src/main/java/org/example/framework/DispatcherServlet.java -->
```java
package org.example.framework;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.example.exception.RouteNotFoundException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class DispatcherServlet extends HttpServlet {

    private final Map<String, Supplier<HttpServlet>> suppliers = new HashMap<>();
    private final Map<String, HttpServlet> instances = new ConcurrentHashMap<>();

    public void register(String routeKey, Supplier<HttpServlet> supplier) {
        suppliers.put(routeKey, supplier);
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String path = request.getPathInfo();
        if (path == null) {
            path = "/";
        }

        String pathWithoutSlash = path.substring(1);
        int slashIndex = pathWithoutSlash.indexOf('/');

        String resource;
        String rest;
        if (slashIndex == -1) {
            resource = pathWithoutSlash;
            rest = "";
        } else {
            resource = pathWithoutSlash.substring(0, slashIndex);
            rest = pathWithoutSlash.substring(slashIndex);
        }

        String routeKey;
        if (rest.isEmpty()) {
            routeKey = resource;
        } else {
            routeKey = resource + "/{id}";
        }
        Supplier<HttpServlet> supplier = suppliers.get(routeKey);
        if (supplier == null) {
            throw new RouteNotFoundException(request.getMethod(), request.getRequestURI());
        }

        HttpServlet servlet = instances.computeIfAbsent(routeKey, key -> supplier.get());
        HttpServletRequest forwardedRequest;
        if (rest.isEmpty()) {
            forwardedRequest = request;
        } else {
            forwardedRequest = new PathInfoRequest(request, rest);
        }
        servlet.service(forwardedRequest, response);
    }

    private static class PathInfoRequest extends HttpServletRequestWrapper {
        private final String pathInfo;

        PathInfoRequest(HttpServletRequest request, String pathInfo) {
            super(request);
            this.pathInfo = pathInfo;
        }

        @Override
        public String getPathInfo() {
            return pathInfo;
        }
    }
}
```

**Ce a dispărut.** Câmpurile pentru cele două servleturi și constructorul. Dispatcher-ul nu mai primește obiecte gata construite.

**Cele două map-uri:**
- **`suppliers` este registry-ul.** Leagă cheia rutei de rețeta controllerului.
- **`instances` este cache-ul.** Păstrează instanțele deja create.

**De ce `computeIfAbsent` pe un `ConcurrentHashMap`.** Tomcat servește request-urile pe mai multe fire de execuție în paralel. Dacă două request-uri ajung simultan la o rută nouă, `computeIfAbsent` pe un `ConcurrentHashMap` garantează că rețeta rulează o singură dată. Cu un `HashMap` obișnuit și un `if`, s-ar putea crea două instanțe.

**De ce `suppliers` poate fi un `HashMap` simplu.** În el se scrie doar în Main, înainte de pornirea serverului. În timpul request-urilor se citește doar.

**If-else-ul rămas nu e pe numele resursei.** El decide doar forma rutei: colecție sau element. Dispare la pasul 5.

### Pasul 2.2: `Main`

Importurile rămân ca la pasul 1. Înregistrarea servleturilor devine:

<!-- verify:main-imports step=2 -->
```java
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.example.config.DatabaseConfig;
import org.example.controller.MobileProductsController;
import org.example.controller.ProductController;
import org.example.controller.ProductsController;
import org.example.framework.DispatcherServlet;
import org.example.middleware.GlobalExceptionFilter;
import org.example.middleware.LoggingFilter;
import org.example.repository.BrandRepository;
import org.example.repository.CategoryRepository;
import org.example.repository.ProductRepository;
import org.example.service.ProductService;
```

<!-- verify:main-wiring step=2 -->
```java
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

        DispatcherServlet dispatcherServlet = new DispatcherServlet();
        dispatcherServlet.register("products", () -> new ProductsController(productService));
        dispatcherServlet.register("products/{id}", () -> new ProductController(productService));

        Tomcat.addServlet(context, "dispatcherServlet", dispatcherServlet);
        Tomcat.addServlet(context, "mobileProductsServlet", new MobileProductsController(productService));

        context.addServletMappingDecoded("/*", "dispatcherServlet");
        context.addServletMappingDecoded("/mobile/products", "mobileProductsServlet");
```

**Main nu mai construiește controllerele de produse.** Înregistrează doar rețetele. O resursă nouă se adaugă acum dintr-o singură linie, fără nicio modificare în dispatcher.

### Verificarea

Răspunsurile sunt identice cu pasul 1. Ca să vezi lazy loading-ul cu ochii tăi, adaugă temporar un `System.out.println` în constructorul lui `ProductsController`. Mesajul apare la primul `GET /products`, nu la pornire, și apare o singură dată. Șterge apoi linia.

### Corespondentul în Spring

`HandlerMapping`, componenta care leagă un URL de un handler.

## 3. Containerul de dependency injection

### Scopul

Main nu mai construiește manual nimic. Un container creează fiecare obiect și îi injectează automat dependențele. Dispatcher-ul îi cere containerului controllerele.

### Conceptul

Notele spuneau: „dă-mi instanța de Products de controller și injectează în el tot ce ai nevoie”. Asta face un **container de dependency injection**. În Spring se numește `ApplicationContext`.

Containerul nostru se bazează pe distincția dintre **definiția clasei** și **instanță**:

- **Un obiect `Class<?>` este definiția clasei.** Conține metadate: numele, constructorii, metodele, câmpurile. Îl obții cu `ProductsController.class`.
- **Instanța apare abia când apelezi un constructor.** Prin reflection, apelul se face cu `constructor.newInstance(argumente)`, echivalentul dinamic al lui `new`.

Algoritmul containerului, pentru o clasă cerută:

1. Dacă instanța există deja, o întoarce. Fiecare clasă are o singură instanță, adică un **singleton**.
2. Altfel, citește singurul constructor public al clasei.
3. Pentru fiecare parametru al constructorului, cere containerului, recursiv, o instanță de tipul respectiv.
4. Apelează constructorul cu aceste instanțe și păstrează rezultatul.

Așa, cererea pentru `ProductsController` declanșează singură crearea lui `ProductService`. Aceasta, la rândul ei, declanșează crearea celor trei repository-uri.

**Problema cu String-urile.** Repository-urile primesc acum trei String-uri: URL-ul, utilizatorul și parola. Un container care lucrează după tip nu are cum să știe ce String să pună pe fiecare poziție. Soluția este ca repository-urile să primească un singur obiect, `DatabaseConfig`. Pe acesta îl înregistrezi manual în container, fiindcă vine din variabilele de mediu, nu dintr-un constructor.

### Ce modifici, în ordine

1. Creezi `Container` în pachetul `framework`.
2. Modifici constructorul celor trei repository-uri.
3. Modifici `DispatcherServlet`.
4. Modifici `Main`.

### Pasul 3.1: `Container`

<!-- verify:file step=3 path=src/main/java/org/example/framework/Container.java -->
```java
package org.example.framework;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Container {

    private final Map<Class<?>, Object> instances = new HashMap<>();
    private final Set<Class<?>> inProgress = new HashSet<>();

    public synchronized <T> void register(Class<T> type, T instance) {
        instances.put(type, instance);
    }

    public synchronized <T> T get(Class<T> type) {
        Object existing = instances.get(type);
        if (existing != null) {
            return type.cast(existing);
        }

        if (inProgress.contains(type)) {
            throw new IllegalStateException("Circular dependency on " + type.getName());
        }

        inProgress.add(type);
        try {
            T created = create(type);
            instances.put(type, created);
            return created;
        } finally {
            inProgress.remove(type);
        }
    }

    private <T> T create(Class<T> type) {
        Constructor<?>[] constructors = type.getConstructors();
        if (constructors.length != 1) {
            throw new IllegalStateException(type.getName() + " must have exactly one public constructor");
        }

        Constructor<?> constructor = constructors[0];
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Object[] arguments = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            arguments[i] = get(parameterTypes[i]);
        }

        try {
            return type.cast(constructor.newInstance(arguments));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not create " + type.getName(), e);
        }
    }
}
```

**De ce metodele sunt `synchronized`.** Două request-uri simultane ar putea cere aceeași clasă pentru prima dată. Fără sincronizare, s-ar putea crea două instanțe. Varianta mai elegantă, `computeIfAbsent`, nu merge aici: `get` se apelează recursiv pe același map, iar `ConcurrentHashMap` interzice modificarea lui din interiorul lui `computeIfAbsent`.

**De ce setul `inProgress`.** Dacă `A` are nevoie de `B`, iar `B` are nevoie de `A`, recursivitatea nu s-ar opri niciodată. Programul ar cădea cu `StackOverflowError`. Setul ține minte clasele aflate în construcție, ca dependența circulară să producă o eroare cu un mesaj clar.

**De ce exact un constructor public.** Cu doi constructori, containerul nu știe pe care să îl aleagă. Spring rezolvă situația cu adnotarea `@Autowired`. Noi preferăm o regulă simplă.

**De ce `try`/`finally`.** Dacă construcția eșuează, clasa trebuie scoasă oricum din `inProgress`. Altfel, următoarea cerere ar raporta o dependență circulară falsă.

### Pasul 3.2: repository-urile

În `ProductRepository`, `BrandRepository` și `CategoryRepository` adaugi importul și înlocuiești constructorul. Exemplul de mai jos e pentru `ProductRepository`. La celelalte două se schimbă doar numele clasei.

<!-- verify:repo-ctor step=3 -->
```java
    public ProductRepository(DatabaseConfig databaseConfig) {
        this.url = databaseConfig.getDbUrl();
        this.user = databaseConfig.getDbUser();
        this.password = databaseConfig.getDbPassword();
    }
```

```java
import org.example.config.DatabaseConfig;
```

Câmpurile și restul clasei rămân neschimbate.

### Pasul 3.3: `DispatcherServlet`

<!-- verify:file step=3 path=src/main/java/org/example/framework/DispatcherServlet.java -->
```java
package org.example.framework;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.example.exception.RouteNotFoundException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DispatcherServlet extends HttpServlet {

    private final Container container;
    private final Map<String, Class<? extends HttpServlet>> routes = new HashMap<>();

    public DispatcherServlet(Container container) {
        this.container = container;
    }

    public void register(String routeKey, Class<? extends HttpServlet> controllerClass) {
        routes.put(routeKey, controllerClass);
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String path = request.getPathInfo();
        if (path == null) {
            path = "/";
        }

        String pathWithoutSlash = path.substring(1);
        int slashIndex = pathWithoutSlash.indexOf('/');

        String resource;
        String rest;
        if (slashIndex == -1) {
            resource = pathWithoutSlash;
            rest = "";
        } else {
            resource = pathWithoutSlash.substring(0, slashIndex);
            rest = pathWithoutSlash.substring(slashIndex);
        }

        String routeKey;
        if (rest.isEmpty()) {
            routeKey = resource;
        } else {
            routeKey = resource + "/{id}";
        }
        Class<? extends HttpServlet> controllerClass = routes.get(routeKey);
        if (controllerClass == null) {
            throw new RouteNotFoundException(request.getMethod(), request.getRequestURI());
        }

        HttpServlet servlet = container.get(controllerClass);
        HttpServletRequest forwardedRequest;
        if (rest.isEmpty()) {
            forwardedRequest = request;
        } else {
            forwardedRequest = new PathInfoRequest(request, rest);
        }
        servlet.service(forwardedRequest, response);
    }

    private static class PathInfoRequest extends HttpServletRequestWrapper {
        private final String pathInfo;

        PathInfoRequest(HttpServletRequest request, String pathInfo) {
            super(request);
            this.pathInfo = pathInfo;
        }

        @Override
        public String getPathInfo() {
            return pathInfo;
        }
    }
}
```

**Ce s-a schimbat.** Registry-ul ține acum definiția clasei, nu o rețetă. Cache-ul propriu din pasul 2 a dispărut, fiindcă singleton-urile le păstrează containerul. Dispatcher-ul nu mai creează nimic. Doar cere.

### Pasul 3.4: `Main`

**Importurile.** Dispar cele trei repository-uri și serviciul, fiindcă Main nu le mai folosește. Apare `Container`.

<!-- verify:main-imports step=3 -->
```java
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.example.config.DatabaseConfig;
import org.example.controller.MobileProductsController;
import org.example.controller.ProductController;
import org.example.controller.ProductsController;
import org.example.framework.Container;
import org.example.framework.DispatcherServlet;
import org.example.middleware.GlobalExceptionFilter;
import org.example.middleware.LoggingFilter;
```

**Înregistrarea.** Tot blocul de construcție manuală dispare:

<!-- verify:main-wiring step=3 -->
```java
        Container container = new Container();
        container.register(DatabaseConfig.class, DatabaseConfig.fromEnvironment());

        DispatcherServlet dispatcherServlet = new DispatcherServlet(container);
        dispatcherServlet.register("products", ProductsController.class);
        dispatcherServlet.register("products/{id}", ProductController.class);

        Tomcat.addServlet(context, "dispatcherServlet", dispatcherServlet);
        Tomcat.addServlet(context, "mobileProductsServlet", container.get(MobileProductsController.class));

        context.addServletMappingDecoded("/*", "dispatcherServlet");
        context.addServletMappingDecoded("/mobile/products", "mobileProductsServlet");
```

**De ce și controllerul de mobile vine din container.** Așa folosește același `ProductService` ca restul controllerelor. Containerul îl creează la pornire, fiindcă Tomcat are nevoie de el înainte de primul request. Odată cu el se creează și serviciul și repository-urile. Controllerele de produse rămân lazy.

### Verificarea

Răspunsurile sunt identice cu pașii anteriori. Pentru a confirma că funcționează tot lanțul construit de container, testează un brand inexistent și o categorie inexistentă:

```
curl -i -X PATCH localhost:8083/products/1 -H "Content-Type: application/json" -d '{"brandId":999999}'
curl -i -X PATCH localhost:8083/products/1 -H "Content-Type: application/json" -d '{"categoryId":999999}'
```

Ambele trebuie să întoarcă 404. Răspunsurile vin din interogări reale pe tabelele de branduri și categorii, deci ambele repository-uri au primit corect configurația.

### Corespondentul în Spring

`ApplicationContext`, cu constructor injection și singleton ca scope implicit.

## 4. Convenția de nume

### Scopul

Ruta `/products` ajunge la controller fără nicio linie de configurare. Numele clasei se calculează din URL.

### Conceptul

Notele spuneau: „faci `capitalize(path)`, iar din `products` obții `Products`”. Adăugând pachetul și sufixul obții numele complet al clasei, cu un șablon de string:

```java
String.format("%s.%sController", "org.example.controller", "Products")
// "org.example.controller.ProductsController"
```

`Class.forName(nume)` transformă apoi String-ul în definiția clasei. Acesta e pasul pe care Java nu îl poate face cu `new`: nu poți scrie `new` urmat de o variabilă de tip String. Reflection o poate face.

Acesta e principiul **convention over configuration**, popularizat de Ruby on Rails. Dacă respecți o regulă de nume, nu mai ai nimic de configurat.

**Product sau products.** URL-urile sunt la plural, conform convenției REST. Rails numește și controllerele la plural, de exemplu `ProductsController`. Cu regula asta, capitalizarea funcționează direct, fără reguli de singularizare. De exemplu, `categories` devine `CategoriesController`, nu un `Categorie` greșit.

**Excepția temporară.** Produsele au încă două controllere. Convenția ar trimite și `/products/123` la `ProductsController`, care ar întoarce lista. De aceea ruta `products/{id}` rămâne înregistrată explicit, iar ruta explicită are prioritate. Excepția dispare la pasul 5.

### Ce modifici, în ordine

1. `DispatcherServlet`
2. `Main`

### Pasul 4.1: `DispatcherServlet`

<!-- verify:file step=4 path=src/main/java/org/example/framework/DispatcherServlet.java -->
```java
package org.example.framework;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.example.exception.RouteNotFoundException;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DispatcherServlet extends HttpServlet {

    private final Container container;
    private final String controllerPackage;
    private final Map<String, Class<? extends HttpServlet>> routes = new ConcurrentHashMap<>();

    public DispatcherServlet(Container container, String controllerPackage) {
        this.container = container;
        this.controllerPackage = controllerPackage;
    }

    public void register(String routeKey, Class<? extends HttpServlet> controllerClass) {
        routes.put(routeKey, controllerClass);
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String path = request.getPathInfo();
        if (path == null) {
            path = "/";
        }

        String pathWithoutSlash = path.substring(1);
        int slashIndex = pathWithoutSlash.indexOf('/');

        String resource;
        String rest;
        if (slashIndex == -1) {
            resource = pathWithoutSlash;
            rest = "";
        } else {
            resource = pathWithoutSlash.substring(0, slashIndex);
            rest = pathWithoutSlash.substring(slashIndex);
        }

        String routeKey;
        if (rest.isEmpty()) {
            routeKey = resource;
        } else {
            routeKey = resource + "/{id}";
        }
        Class<? extends HttpServlet> controllerClass = routes.get(routeKey);
        if (controllerClass == null) {
            controllerClass = findControllerByConvention(resource);
            if (controllerClass != null) {
                routes.put(routeKey, controllerClass);
            }
        }
        if (controllerClass == null) {
            throw new RouteNotFoundException(request.getMethod(), request.getRequestURI());
        }

        HttpServlet servlet = container.get(controllerClass);
        HttpServletRequest forwardedRequest;
        if (rest.isEmpty()) {
            forwardedRequest = request;
        } else {
            forwardedRequest = new PathInfoRequest(request, rest);
        }
        servlet.service(forwardedRequest, response);
    }

    private Class<? extends HttpServlet> findControllerByConvention(String resource) {
        if (!resource.matches("[a-z]+")) {
            return null;
        }

        String className = String.format("%s.%sController", controllerPackage, capitalize(resource));
        Class<?> foundClass;
        try {
            foundClass = Class.forName(className);
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return null;
        }

        if (!HttpServlet.class.isAssignableFrom(foundClass)) {
            return null;
        }
        return foundClass.asSubclass(HttpServlet.class);
    }

    private String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static class PathInfoRequest extends HttpServletRequestWrapper {
        private final String pathInfo;

        PathInfoRequest(HttpServletRequest request, String pathInfo) {
            super(request);
            this.pathInfo = pathInfo;
        }

        @Override
        public String getPathInfo() {
            return pathInfo;
        }
    }
}
```

**De ce pachetul vine din constructor.** Dacă dispatcher-ul ar conține string-ul „org.example.controller”, framework-ul ar ști ceva despre aplicație. Main îi transmite pachetul, iar framework-ul rămâne generic.

**Ordinea căutării.** Întâi se caută o rută explicită, sau una găsită deja prin convenție. Abia apoi se încearcă convenția. Clasa găsită se pune în `routes`, ca următorul request să nu mai apeleze `Class.forName`.

**De ce `routes` e acum `ConcurrentHashMap`.** Convenția scrie în el în timpul request-urilor, de pe mai multe fire de execuție simultan.

**De ce doar litere mici.** Numele resursei vine direct din URL, deci de la oricine trimite request-ul. Fără validare, un URL ca `/x.Evil` sau `/x$y` ar putea alege clase neintenționate.

**De ce se prinde și `NoClassDefFoundError`.** Explicația e la capitolul 7, capcana 3.

**De ce se verifică `HttpServlet`.** O clasă cu numele potrivit, dar care nu e servlet, nu poate primi request-uri. O tratăm ca și cum n-ar exista.

### Pasul 4.2: `Main`

Dispare importul lui `ProductsController`, fiindcă Main nu mai înregistrează ruta `/products`.

<!-- verify:main-imports step=4 -->
```java
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.example.config.DatabaseConfig;
import org.example.controller.MobileProductsController;
import org.example.controller.ProductController;
import org.example.framework.Container;
import org.example.framework.DispatcherServlet;
import org.example.middleware.GlobalExceptionFilter;
import org.example.middleware.LoggingFilter;
```

<!-- verify:main-wiring step=4 -->
```java
        Container container = new Container();
        container.register(DatabaseConfig.class, DatabaseConfig.fromEnvironment());

        DispatcherServlet dispatcherServlet = new DispatcherServlet(container, "org.example.controller");
        dispatcherServlet.register("products/{id}", ProductController.class);

        Tomcat.addServlet(context, "dispatcherServlet", dispatcherServlet);
        Tomcat.addServlet(context, "mobileProductsServlet", container.get(MobileProductsController.class));

        context.addServletMappingDecoded("/*", "dispatcherServlet");
        context.addServletMappingDecoded("/mobile/products", "mobileProductsServlet");
```

### Verificarea

Răspunsurile sunt identice cu pașii anteriori. Testează și câteva cazuri limită ale convenției. Toate trebuie să întoarcă 404:

```
curl -i localhost:8083/Products
curl -i localhost:8083/productsX
curl -i localhost:8083/mobileproducts
curl -i "localhost:8083/products.Evil"
```

**Un efect secundar temporar.** `/product/1`, la singular, devine prin convenție `ProductController`, iar clasa aceasta există. Așa apare un al doilea URL pentru produse. Testează-l doar cu GET. Efectul dispare la pasul 5, când `ProductController` se șterge.

### Corespondentul în Spring

Spring nu folosește convenții de nume pentru rute, ci adnotări. Convenția vine din Rails. Încărcarea dinamică a claselor după nume e însă exact ce face Spring când citește configurații sau scanează pachete.

## 5. Controller propriu, detașat de HttpServlet

### Scopul

Există un singur `ProductsController`, care nu mai extinde `HttpServlet`. Are șase metode simple, primește valori Java și întoarce obiecte. Dispatcher-ul face toată partea de HTTP.

### Conceptul

Notele întrebau: „poate controllerul să fie total detașat de HttpServlet?”. Răspunsul e da, dacă dispatcher-ul preia trei responsabilități:

1. **Alege metoda.** Folosește un tabel care leagă verbul și forma rutei de numele metodei:

   | Request | Metodă |
   |---|---|
   | `GET /products` | `getAll` |
   | `GET /products/123` | `getOne` |
   | `POST /products` | `create` |
   | `PUT /products/123` | `update` |
   | `PATCH /products/123` | `patch` |
   | `DELETE /products/123` | `delete` |

2. **Construiește argumentele.** Se uită la tipul fiecărui parametru al metodei, prin reflection:

   | Tipul parametrului | Valoarea primită |
   |---|---|
   | `int` | Id-ul din URL, validat de dispatcher |
   | `RequestParams` | Parametrii din query string |
   | Orice alt tip | Body-ul JSON, citit ca acel tip |

3. **Apelează metoda și scrie răspunsul.** Apelul dinamic se face cu `method.invoke(controller, argumente)`. Obiectul întors devine JSON, cu statusul 201 pentru `create` și 200 pentru restul.

În Spring, punctul 1 e treaba lui `HandlerMapping`, punctul 2 a argument resolver-ilor, iar punctul 3 a lui `HandlerAdapter` și a convertorilor de mesaje.

**Un detaliu important despre `Method.invoke`.** Dacă metoda apelată aruncă o excepție, reflection o învelește într-o `InvocationTargetException`. Dacă nu o despachetezi, `GlobalExceptionFilter` nu mai recunoaște `ProductNotFoundException`, iar un produs inexistent ar da 500 în loc de 404.

### Ce modifici, în ordine

1. Creezi `RequestParams` în pachetul `framework`.
2. Creezi `BadRequestException` și `MethodNotAllowedException` în pachetul `exception`.
3. Rescrii `DispatcherServlet`.
4. Rescrii `ProductsController`.
5. Ștergi `ProductController`.
6. Modifici `GlobalExceptionFilter`.
7. Modifici `Main`.
8. Rescrii `TestProductsController`.
9. Faci un build curat.

### Pasul 5.1: `RequestParams`

<!-- verify:file step=5 path=src/main/java/org/example/framework/RequestParams.java -->
```java
package org.example.framework;

import java.util.Map;

public class RequestParams {

    private final Map<String, String[]> values;

    public RequestParams(Map<String, String[]> values) {
        this.values = values;
    }

    public String get(String name) {
        String[] parameterValues = values.get(name);
        if (parameterValues == null || parameterValues.length == 0) {
            return null;
        }
        return parameterValues[0];
    }
}
```

**De ce primește un `Map`, nu `HttpServletRequest`.** Un test poate construi parametrii direct, cu `Map.of(...)`, fără mock-uri de servlet. Controllerul nu mai depinde deloc de API-ul de servlet.

### Pasul 5.2: cele două excepții

<!-- verify:file step=5 path=src/main/java/org/example/exception/BadRequestException.java -->
```java
package org.example.exception;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
```

<!-- verify:file step=5 path=src/main/java/org/example/exception/MethodNotAllowedException.java -->
```java
package org.example.exception;

public class MethodNotAllowedException extends RuntimeException {
    public MethodNotAllowedException(String method, String uri) {
        super("Method " + method + " is not allowed on " + uri);
    }
}
```

### Pasul 5.3: `DispatcherServlet`, varianta finală

<!-- verify:file step=5 path=src/main/java/org/example/framework/DispatcherServlet.java -->
```java
package org.example.framework;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.exception.BadRequestException;
import org.example.exception.MethodNotAllowedException;
import org.example.exception.RouteNotFoundException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DispatcherServlet extends HttpServlet {

    private static final Map<String, String> ACTIONS = Map.of(
            "GET", "getAll",
            "GET/{id}", "getOne",
            "POST", "create",
            "PUT/{id}", "update",
            "PATCH/{id}", "patch",
            "DELETE/{id}", "delete"
    );
    private static final Map<String, Integer> STATUS_CODES = Map.of("create", 201);

    private final Container container;
    private final String controllerPackage;
    private final Map<String, Class<?>> routes = new ConcurrentHashMap<>();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public DispatcherServlet(Container container, String controllerPackage) {
        this.container = container;
        this.controllerPackage = controllerPackage;
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String path = request.getPathInfo();
        if (path == null) {
            path = "/";
        }

        String pathWithoutSlash = path.substring(1);
        int slashIndex = pathWithoutSlash.indexOf('/');

        String resource;
        String rest;
        if (slashIndex == -1) {
            resource = pathWithoutSlash;
            rest = "";
        } else {
            resource = pathWithoutSlash.substring(0, slashIndex);
            rest = pathWithoutSlash.substring(slashIndex);
        }

        Class<?> controllerClass = routes.get(resource);
        if (controllerClass == null) {
            controllerClass = findControllerByConvention(resource);
            if (controllerClass != null) {
                routes.put(resource, controllerClass);
            }
        }
        if (controllerClass == null) {
            throw new RouteNotFoundException(request.getMethod(), request.getRequestURI());
        }

        String actionKey;
        if (rest.isEmpty()) {
            actionKey = request.getMethod();
        } else {
            actionKey = request.getMethod() + "/{id}";
        }
        String methodName = ACTIONS.get(actionKey);
        if (methodName == null) {
            throw new MethodNotAllowedException(request.getMethod(), request.getRequestURI());
        }
        Method method = findMethod(controllerClass, methodName);
        if (method == null) {
            throw new MethodNotAllowedException(request.getMethod(), request.getRequestURI());
        }

        Object controller = container.get(controllerClass);
        Object[] arguments = resolveArguments(method, request, rest);
        Object result = invoke(method, controller, arguments);

        response.setStatus(STATUS_CODES.getOrDefault(methodName, 200));
        response.setContentType("application/json; charset=UTF-8");
        response.getWriter().write(jsonMapper.writeValueAsString(result));
    }

    private Method findMethod(Class<?> controllerClass, String methodName) {
        for (Method method : controllerClass.getMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    private Object[] resolveArguments(Method method, HttpServletRequest request, String rest) throws IOException {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] arguments = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (parameterType == int.class) {
                arguments[i] = parseId(rest);
            } else if (parameterType == RequestParams.class) {
                arguments[i] = new RequestParams(request.getParameterMap());
            } else {
                arguments[i] = jsonMapper.readValue(request.getReader(), parameterType);
            }
        }
        return arguments;
    }

    private int parseId(String rest) {
        if (!rest.matches("/[0-9]{1,9}")) {
            throw new BadRequestException("Id must be a positive integer");
        }
        int id = Integer.parseInt(rest.substring(1));
        if (id <= 0) {
            throw new BadRequestException("Id must be a positive integer");
        }
        return id;
    }

    private Object invoke(Method method, Object controller, Object[] arguments) {
        try {
            return method.invoke(controller, arguments);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private Class<?> findControllerByConvention(String resource) {
        if (!resource.matches("[a-z]+")) {
            return null;
        }

        String className = String.format("%s.%sController", controllerPackage, capitalize(resource));
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return null;
        }
    }

    private String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
```

**Ce a dispărut:**
- **`PathInfoRequest`.** Controllerul nu mai citește path-ul, deci nu mai are nevoie de el.
- **Cheia de rută cu `{id}`.** Fiecare resursă are acum un singur controller, deci cheia e doar numele resursei.
- **Metoda `register` și verificarea `HttpServlet`.** Orice clasă cu numele potrivit e acum un controller.

**Ce e nou:**
- **`ACTIONS`** e tabelul de la începutul pasului. O combinație lipsă din tabel, de exemplu `DELETE /products`, înseamnă 405.
- **`findMethod`** caută metoda după nume pe clasa controllerului. Un controller fără metoda respectivă înseamnă tot 405.
- **`resolveArguments`** construiește argumentele după tipul parametrilor.
- **`parseId`** validează id-ul o singură dată, pentru toate resursele. Mesajul de eroare este acum corect: „Id must be a positive integer”, nu „Invalid request body”.
- **`invoke`** apelează metoda și despachetează excepțiile.

### Pasul 5.4: `ProductsController`

<!-- verify:file step=5 path=src/main/java/org/example/controller/ProductsController.java -->
```java
package org.example.controller;

import org.example.dto.PatchProductRequest;
import org.example.dto.ProductWriteRequest;
import org.example.framework.RequestParams;
import org.example.model.Product;
import org.example.service.ProductService;

import java.util.List;

public class ProductsController {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 100;
    private static final int MAX_SIZE = 1000;
    private static final int MAX_DIGITS = 9;

    private final ProductService productService;

    public ProductsController(ProductService productService) {
        this.productService = productService;
    }

    public List<Product> getAll(RequestParams params) {
        int page = parsePositiveIntOrDefault(params, "page", DEFAULT_PAGE);
        int size = parsePositiveIntOrDefault(params, "size", DEFAULT_SIZE);

        if(size > MAX_SIZE){
            size = MAX_SIZE;
        }

        List<Product> products = productService.getProduct(page,size);
        return products;
    }

    public Product getOne(int id) {
        return productService.getProductById(id);
    }

    public Product create(ProductWriteRequest body) {
        return productService.createProduct(body);
    }

    public Product update(int id, ProductWriteRequest body) {
        return productService.updateProductById(id, body);
    }

    public Product patch(int id, PatchProductRequest body) {
        return productService.updateProductFieldsById(id, body);
    }

    public Product delete(int id) {
        return productService.deleteProductById(id);
    }

    private int parsePositiveIntOrDefault(RequestParams params, String parameterName, int defaultValue){
        int result = defaultValue;

        String text = params.get(parameterName);
        if(isPositiveNaturalNumber(text)){
            result = Integer.parseInt(text);
        }

        return result;
    }

    private boolean isPositiveNaturalNumber (String text){
        if(text == null || text.isEmpty()){
            return false;
        }
        if(text.length() > MAX_DIGITS){
            return false;
        }
        for(char character : text.toCharArray()){
            if(!Character.isDigit(character)){
                return false;
            }
        }
        return Integer.parseInt(text) > 0;
    }
}
```

**Compară cu varianta veche.** Nu mai există `HttpServletRequest`, `HttpServletResponse`, JsonMapper, `setStatus` sau `sendError`. Fiecare metodă are o singură responsabilitate: traduce acțiunea HTTP într-un apel către serviciu. Metodele `update`, `patch` și `delete` vin din fostul `ProductController`.

### Pasul 5.5: ștergi `ProductController`

```
rm src/main/java/org/example/controller/ProductController.java
```

<!-- verify:delete step=5 path=src/main/java/org/example/controller/ProductController.java -->

### Pasul 5.6: `GlobalExceptionFilter`

Adaugi tratarea celor două excepții noi. Ele trebuie prinse înaintea blocului pentru `RuntimeException`. Altfel, acel bloc le-ar prinde primul și ar răspunde cu 500.

<!-- verify:file step=5 path=src/main/java/org/example/middleware/GlobalExceptionFilter.java -->
```java
package org.example.middleware;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import org.example.dto.ErrorResponse;
import org.example.exception.BadRequestException;
import org.example.exception.DataAccessException;
import org.example.exception.MethodNotAllowedException;
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
        } catch (BadRequestException e) {
            sendError(httpResponse, 400, "Bad Request", e.getMessage());
        } catch (MethodNotAllowedException e) {
            sendError(httpResponse, 405, "Method Not Allowed", e.getMessage());
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
```

### Pasul 5.7: `Main`

Dispare importul lui `ProductController` și ultima rută explicită. Main nu mai știe nimic despre rutele de produse.

<!-- verify:main-imports step=5 -->
```java
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.example.config.DatabaseConfig;
import org.example.controller.MobileProductsController;
import org.example.framework.Container;
import org.example.framework.DispatcherServlet;
import org.example.middleware.GlobalExceptionFilter;
import org.example.middleware.LoggingFilter;
```

<!-- verify:main-wiring step=5 -->
```java
        Container container = new Container();
        container.register(DatabaseConfig.class, DatabaseConfig.fromEnvironment());

        DispatcherServlet dispatcherServlet = new DispatcherServlet(container, "org.example.controller");

        Tomcat.addServlet(context, "dispatcherServlet", dispatcherServlet);
        Tomcat.addServlet(context, "mobileProductsServlet", container.get(MobileProductsController.class));

        context.addServletMappingDecoded("/*", "dispatcherServlet");
        context.addServletMappingDecoded("/mobile/products", "mobileProductsServlet");
```

### Pasul 5.8: `TestProductsController`

Testul vechi apela `doGet`, care nu mai există. Al doilea test pica de fapt chiar înainte de router. El aștepta ca controllerul să transforme singur eroarea de bază de date în 503, deși responsabilitatea aceasta trecuse deja la `GlobalExceptionFilter`. Varianta nouă verifică exact ce face acum controllerul: lasă eroarea să treacă neschimbată spre filtru.

<!-- verify:file step=5 path=src/test/java/org/example/controller/TestProductsController.java -->
```java
package org.example.controller;

import org.example.exception.DataAccessException;
import org.example.framework.RequestParams;
import org.example.service.ProductService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;

public class TestProductsController {


    @Test
    void requested_product_count_is_limited_to_the_maximum_allowed_size() {

        //Arrage
        RequestParams params = new RequestParams(Map.of("page", new String[]{"2"}, "size", new String[]{"1001"}));
        ProductService productService = mock(ProductService.class);
        ProductsController productsController = new ProductsController(productService);

        Mockito.when(productService.getProduct(Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(List.of());

        //Act
        productsController.getAll(params);

        //Assert
        Mockito.verify(productService).getProduct(2, 1000);
    }

    @Test
    void databaseUnavailability_is_passed_on_to_the_exception_filter() {

        //Arrange
        RequestParams params = new RequestParams(Map.of("page", new String[]{"2"}, "size", new String[]{"10"}));

        ProductService productService = mock(ProductService.class);
        SQLException cause = new SQLException("Database connection failed");

        Mockito.when(productService.getProduct(2, 10)).thenThrow(new DataAccessException("Could not load products", cause));

        ProductsController productsController = new ProductsController(productService);

        //Act
        DataAccessException thrown = Assertions.assertThrows(DataAccessException.class, () -> productsController.getAll(params));

        //Assert
        Assertions.assertEquals("Could not load products", thrown.getMessage());
    }
}
```

### Pasul 5.9: build curat

```
mvn clean compile test-compile
```

**Pasul ăsta e obligatoriu.** Fără el, fișierul compilat vechi `ProductController.class` rămâne în `target`, iar convenția îl găsește în continuare. Detaliile sunt la capitolul 7, capcana 4. Dacă rulezi din IntelliJ, folosește **Build > Rebuild Project**.

### Verificarea

Rulează toată suita de teste, cu aplicația pornită:

```
mvn test
```

Toate cele 7 teste trebuie să treacă: cele 5 E2E și cele 2 unitare.

**Ce se schimbă intenționat față de pașii anteriori:**

| Request | Înainte | Acum |
|---|---|---|
| `GET /products/abc` | 400, „Invalid request body” | 400, „Id must be a positive integer” |
| `DELETE /products` | 405, pagină HTML | 405, JSON |
| `GET /product/1` | 200, aliasul de la pasul 4 | 404 |
| `HEAD` și `OPTIONS` pe `/products` | Tratate automat de HttpServlet | 405 |

Ultimul rând e o pierdere reală, dar API-ul nu folosește aceste verbe. Restul răspunsurilor sunt identice.

### Corespondentul în Spring

`@RestController` cu metode care întorc obiecte, `HandlerAdapter`, argument resolver-ii și `HttpMessageConverter`.

## 6. Rute declarate cu adnotări

### Scopul

Rutele se declară direct pe controller, cu adnotări proprii: `@Controller("/products")` pe clasă, `@Get("/{id}")` pe metodă. Convenția de nume și tabelul fix de acțiuni dispar. Ruta `/mobile/products` intră și ea în router, deci Tomcat rămâne cu un singur servlet.

### Conceptul

**Limitele pasului 5.** Convenția lega URL-ul de numele clasei. Ruta avea cel mult două segmente, resursa și id-ul, iar numele metodelor erau impuse de tabelul din dispatcher. De aceea `/mobile/products` stătea în afara router-ului.

**O adnotare este o etichetă cu date,** atașată unei clase, unei metode sau unui parametru. Ea nu face nimic singură. Contează doar codul care o citește, aici dispatcher-ul, prin reflection. Spring funcționează la fel: `@GetMapping` e doar o etichetă, iar `DispatcherServlet` o citește la pornire.

**`@Retention(RetentionPolicy.RUNTIME)` este obligatorie.** Fără ea, adnotarea ajunge în fișierul `.class`, dar JVM-ul nu o mai expune prin reflection. `getAnnotation` ar întoarce `null`, iar ruta n-ar exista.

**Meta-adnotări.** Ar fi nevoie de cinci adnotări de verb: `@Get`, `@Post`, `@Put`, `@Patch`, `@Delete`. Dispatcher-ul trebuie să afle verbul fiecăreia fără un if-else pe tipul adnotării. Soluția e o adnotare pusă pe adnotări: `@Get` este marcată cu `@HttpMethod("GET")`. Dispatcher-ul caută `@HttpMethod` pe tipul oricărei adnotări găsite pe o metodă. Spring face exact la fel: `@GetMapping` este marcată cu `@RequestMapping(method = RequestMethod.GET)`.

**Șabloane de rută.** Un șablon ca `/products/{id}` se compară cu path-ul segment cu segment. Un segment fix trebuie să fie identic. Un segment `{id}` acceptă orice valoare și o păstrează sub numele `id`. Parametrul metodei marcat cu `@PathVariable("id")` primește apoi această valoare.

**Tabela de rute se construiește o singură dată, la pornire.** Main îi dă dispatcher-ului lista explicită de controllere. Dispatcher-ul le citește adnotările și construiește tabela. Controllerele rămân totuși lazy: tabela ține doar clasa și metoda, iar instanța se cere containerului la primul request.

**404 sau 405.** Dacă path-ul nu se potrivește cu niciun șablon, răspunsul e 404. Dacă path-ul se potrivește, dar niciun verb, răspunsul e 405.

### Ce modifici, în ordine

1. Creezi pachetul `org.example.framework.annotation`, cu nouă adnotări.
2. Rescrii `DispatcherServlet`.
3. Adaugi adnotările în `ProductsController`.
4. Rescrii `MobileProductsController`.
5. Modifici `Main`.
6. Faci un build curat.

### Pasul 6.1: adnotările

Prima e meta-adnotarea. Ea stă doar pe alte adnotări, de aceea are `@Target(ElementType.ANNOTATION_TYPE)`.

<!-- verify:file step=6 path=src/main/java/org/example/framework/annotation/HttpMethod.java -->
```java
package org.example.framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface HttpMethod {
    String value();
}
```

Cele cinci adnotări de verb sunt identice, cu excepția numelui și a verbului din `@HttpMethod`. Valoarea lor implicită este șirul gol, adică ruta e chiar path-ul controllerului.

<!-- verify:file step=6 path=src/main/java/org/example/framework/annotation/Get.java -->
```java
package org.example.framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@HttpMethod("GET")
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Get {
    String value() default "";
}
```

<!-- verify:file step=6 path=src/main/java/org/example/framework/annotation/Post.java -->
```java
package org.example.framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@HttpMethod("POST")
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Post {
    String value() default "";
}
```

<!-- verify:file step=6 path=src/main/java/org/example/framework/annotation/Put.java -->
```java
package org.example.framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@HttpMethod("PUT")
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Put {
    String value() default "";
}
```

<!-- verify:file step=6 path=src/main/java/org/example/framework/annotation/Patch.java -->
```java
package org.example.framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@HttpMethod("PATCH")
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Patch {
    String value() default "";
}
```

<!-- verify:file step=6 path=src/main/java/org/example/framework/annotation/Delete.java -->
```java
package org.example.framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@HttpMethod("DELETE")
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Delete {
    String value() default "";
}
```

`@Controller` stă pe clasă și dă path-ul de bază. În Spring, rolul ei e împărțit între `@RestController` și `@RequestMapping`.

<!-- verify:file step=6 path=src/main/java/org/example/framework/annotation/Controller.java -->
```java
package org.example.framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Controller {
    String value();
}
```

`@PathVariable` stă pe un parametru, de aceea are `@Target(ElementType.PARAMETER)`.

<!-- verify:file step=6 path=src/main/java/org/example/framework/annotation/PathVariable.java -->
```java
package org.example.framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface PathVariable {
    String value();
}
```

`@ResponseStatus` înlocuiește tabelul de statusuri din dispatcher.

<!-- verify:file step=6 path=src/main/java/org/example/framework/annotation/ResponseStatus.java -->
```java
package org.example.framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ResponseStatus {
    int value();
}
```

**De ce un sub-pachet.** Adnotările sunt API-ul public al framework-ului: singurul lucru pe care aplicația îl vede din el, în afară de `Container`, `DispatcherServlet` și `RequestParams`. Separarea face granița vizibilă.

### Pasul 6.2: `DispatcherServlet`, varianta finală

<!-- verify:file step=6 path=src/main/java/org/example/framework/DispatcherServlet.java -->
```java
package org.example.framework;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.exception.BadRequestException;
import org.example.exception.MethodNotAllowedException;
import org.example.exception.RouteNotFoundException;
import org.example.framework.annotation.Controller;
import org.example.framework.annotation.HttpMethod;
import org.example.framework.annotation.PathVariable;
import org.example.framework.annotation.ResponseStatus;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DispatcherServlet extends HttpServlet {

    private final Container container;
    private final List<Route> routes = new ArrayList<>();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public DispatcherServlet(Container container) {
        this.container = container;
    }

    public void registerController(Class<?> controllerClass) {
        Controller controller = controllerClass.getAnnotation(Controller.class);
        if (controller == null) {
            throw new IllegalArgumentException(controllerClass.getName() + " is not annotated with @Controller");
        }
        for (Method method : controllerClass.getMethods()) {
            for (Annotation annotation : method.getAnnotations()) {
                HttpMethod httpMethod = annotation.annotationType().getAnnotation(HttpMethod.class);
                if (httpMethod != null) {
                    String template = controller.value() + readPath(annotation);
                    routes.add(new Route(httpMethod.value(), template.split("/", -1), controllerClass, method));
                }
            }
        }
    }

    private String readPath(Annotation annotation) {
        try {
            return (String) annotation.annotationType().getMethod("value").invoke(annotation);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(annotation.annotationType().getName() + " must declare String value()", e);
        }
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String path = request.getPathInfo();
        if (path == null) {
            path = "/";
        }
        String[] pathSegments = path.split("/", -1);

        boolean pathMatched = false;
        for (Route route : routes) {
            Map<String, String> pathVariables = match(route.segments(), pathSegments);
            if (pathVariables == null) {
                continue;
            }
            pathMatched = true;
            if (!route.httpMethod().equals(request.getMethod())) {
                continue;
            }
            handle(route, pathVariables, request, response);
            return;
        }

        if (pathMatched) {
            throw new MethodNotAllowedException(request.getMethod(), request.getRequestURI());
        }
        throw new RouteNotFoundException(request.getMethod(), request.getRequestURI());
    }

    private Map<String, String> match(String[] templateSegments, String[] pathSegments) {
        if (templateSegments.length != pathSegments.length) {
            return null;
        }
        Map<String, String> pathVariables = new HashMap<>();
        for (int i = 0; i < templateSegments.length; i++) {
            String templateSegment = templateSegments[i];
            if (templateSegment.startsWith("{") && templateSegment.endsWith("}")) {
                pathVariables.put(templateSegment.substring(1, templateSegment.length() - 1), pathSegments[i]);
            } else if (!templateSegment.equals(pathSegments[i])) {
                return null;
            }
        }
        return pathVariables;
    }

    private void handle(Route route, Map<String, String> pathVariables, HttpServletRequest request, HttpServletResponse response) throws IOException {
        Object controller = container.get(route.controllerClass());
        Object[] arguments = resolveArguments(route.method(), pathVariables, request);
        Object result = invoke(route.method(), controller, arguments);

        int status = 200;
        ResponseStatus responseStatus = route.method().getAnnotation(ResponseStatus.class);
        if (responseStatus != null) {
            status = responseStatus.value();
        }
        response.setStatus(status);
        response.setContentType("application/json; charset=UTF-8");
        response.getWriter().write(jsonMapper.writeValueAsString(result));
    }

    private Object[] resolveArguments(Method method, Map<String, String> pathVariables, HttpServletRequest request) throws IOException {
        Parameter[] parameters = method.getParameters();
        Object[] arguments = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            PathVariable pathVariable = parameters[i].getAnnotation(PathVariable.class);
            if (pathVariable != null) {
                arguments[i] = parseId(pathVariables.get(pathVariable.value()));
            } else if (parameters[i].getType() == RequestParams.class) {
                arguments[i] = new RequestParams(request.getParameterMap());
            } else {
                arguments[i] = jsonMapper.readValue(request.getReader(), parameters[i].getType());
            }
        }
        return arguments;
    }

    private int parseId(String value) {
        if (value == null || !value.matches("[0-9]{1,9}")) {
            throw new BadRequestException("Id must be a positive integer");
        }
        int id = Integer.parseInt(value);
        if (id <= 0) {
            throw new BadRequestException("Id must be a positive integer");
        }
        return id;
    }

    private Object invoke(Method method, Object controller, Object[] arguments) {
        try {
            return method.invoke(controller, arguments);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Route(String httpMethod, String[] segments, Class<?> controllerClass, Method method) {
    }
}
```

**Ce a dispărut:** `controllerPackage`, convenția de nume cu `capitalize`, tabelele `ACTIONS` și `STATUS_CODES`, `findMethod` și împărțirea path-ului în resursă și rest.

**Ce e nou:**
- **`registerController`** citește `@Controller` de pe clasă. O clasă fără adnotare produce o eroare la pornire, nu la primul request. Pentru fiecare metodă, caută printre adnotări pe cele marcate cu `@HttpMethod` și adaugă câte o rută.
- **`readPath`** citește `value()` din orice adnotare de verb, fără să știe care este. Atributele unei adnotări sunt, tehnic, metode, deci se pot apela prin reflection ca orice metodă.
- **`split("/", -1)`.** Argumentul `-1` păstrează segmentele goale. Fără el, `/products/` ar avea aceleași segmente ca `/products`, iar request-ul ar ajunge la `getAll`. Cu el, `/products/` se potrivește cu `/products/{id}`, cu un id gol, și primește 400, ca înainte.
- **`match`** compară șablonul cu path-ul, segment cu segment, și întoarce variabilele găsite sau `null`.
- **`service`** caută prima rută care se potrivește și ca path, și ca verb. Variabila `pathMatched` face diferența dintre 404 și 405.
- **`handle`** cere controllerul containerului, construiește argumentele, apelează metoda și alege statusul din `@ResponseStatus`.
- **`resolveArguments`** folosește acum `Parameter`, nu doar tipul, ca să poată citi `@PathVariable` de pe fiecare parametru.
- **`Route`** este un `record`: o clasă de date imutabilă, cu constructor și metode de acces generate automat.

### Pasul 6.3: `ProductsController`

Logica rămâne neschimbată. Se adaugă doar adnotările.

<!-- verify:file step=6 path=src/main/java/org/example/controller/ProductsController.java -->
```java
package org.example.controller;

import org.example.dto.PatchProductRequest;
import org.example.dto.ProductWriteRequest;
import org.example.framework.RequestParams;
import org.example.framework.annotation.Controller;
import org.example.framework.annotation.Delete;
import org.example.framework.annotation.Get;
import org.example.framework.annotation.Patch;
import org.example.framework.annotation.PathVariable;
import org.example.framework.annotation.Post;
import org.example.framework.annotation.Put;
import org.example.framework.annotation.ResponseStatus;
import org.example.model.Product;
import org.example.service.ProductService;

import java.util.List;

@Controller("/products")
public class ProductsController {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 100;
    private static final int MAX_SIZE = 1000;
    private static final int MAX_DIGITS = 9;

    private final ProductService productService;

    public ProductsController(ProductService productService) {
        this.productService = productService;
    }

    @Get
    public List<Product> getAll(RequestParams params) {
        int page = parsePositiveIntOrDefault(params, "page", DEFAULT_PAGE);
        int size = parsePositiveIntOrDefault(params, "size", DEFAULT_SIZE);

        if(size > MAX_SIZE){
            size = MAX_SIZE;
        }

        List<Product> products = productService.getProduct(page,size);
        return products;
    }

    @Get("/{id}")
    public Product getOne(@PathVariable("id") int id) {
        return productService.getProductById(id);
    }

    @Post
    @ResponseStatus(201)
    public Product create(ProductWriteRequest body) {
        return productService.createProduct(body);
    }

    @Put("/{id}")
    public Product update(@PathVariable("id") int id, ProductWriteRequest body) {
        return productService.updateProductById(id, body);
    }

    @Patch("/{id}")
    public Product patch(@PathVariable("id") int id, PatchProductRequest body) {
        return productService.updateProductFieldsById(id, body);
    }

    @Delete("/{id}")
    public Product delete(@PathVariable("id") int id) {
        return productService.deleteProductById(id);
    }

    private int parsePositiveIntOrDefault(RequestParams params, String parameterName, int defaultValue){
        int result = defaultValue;

        String text = params.get(parameterName);
        if(isPositiveNaturalNumber(text)){
            result = Integer.parseInt(text);
        }

        return result;
    }

    private boolean isPositiveNaturalNumber (String text){
        if(text == null || text.isEmpty()){
            return false;
        }
        if(text.length() > MAX_DIGITS){
            return false;
        }
        for(char character : text.toCharArray()){
            if(!Character.isDigit(character)){
                return false;
            }
        }
        return Integer.parseInt(text) > 0;
    }
}
```

**Numele metodelor nu mai contează pentru rutare.** Ai putea redenumi `getOne` în `findById`, iar ruta ar rămâne aceeași. Nici numele clasei nu mai contează.

### Pasul 6.4: `MobileProductsController`

Nu mai extinde `HttpServlet` și devine un controller ca oricare altul.

<!-- verify:file step=6 path=src/main/java/org/example/controller/MobileProductsController.java -->
```java
package org.example.controller;

import org.example.dto.MobileProductsResponse;
import org.example.framework.annotation.Controller;
import org.example.framework.annotation.Get;
import org.example.service.ProductService;

@Controller("/mobile/products")
public class MobileProductsController {

    private final ProductService productService;

    public MobileProductsController(ProductService productService) {
        this.productService = productService;
    }

    @Get
    public MobileProductsResponse getAll() {
        MobileProductsResponse mobileProducts = productService.getMobileProducts();

        return mobileProducts;
    }
}
```

### Pasul 6.5: `Main`

Apare importul lui `ProductsController`, fiindcă Main îl înregistrează explicit.

<!-- verify:main-imports step=6 -->
```java
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.example.config.DatabaseConfig;
import org.example.controller.MobileProductsController;
import org.example.controller.ProductsController;
import org.example.framework.Container;
import org.example.framework.DispatcherServlet;
import org.example.middleware.GlobalExceptionFilter;
import org.example.middleware.LoggingFilter;
```

Tomcat rămâne cu un singur servlet, iar maparea separată pentru mobile dispare:

<!-- verify:main-wiring step=6 -->
```java
        Container container = new Container();
        container.register(DatabaseConfig.class, DatabaseConfig.fromEnvironment());

        DispatcherServlet dispatcherServlet = new DispatcherServlet(container);
        dispatcherServlet.registerController(ProductsController.class);
        dispatcherServlet.registerController(MobileProductsController.class);

        Tomcat.addServlet(context, "dispatcherServlet", dispatcherServlet);
        context.addServletMappingDecoded("/*", "dispatcherServlet");
```

**De ce o listă explicită.** Alternativa este scanarea automată a pachetului, ca `@ComponentScan` din Spring. Codul ar parcurge folderul `target/classes`, sau intrările dintr-un JAR, și ar încărca fiecare clasă marcată cu `@Controller`. E mult cod, pentru două medii diferite. Ar avea și riscul din capitolul 7, capcana 4: clasele compilate vechi ar fi descoperite și ele. Lista explicită costă câte o linie pentru fiecare controller.

### Pasul 6.6: build curat

```
mvn clean compile test-compile
```

Tot ca la pasul 5, nu vrei fișiere `.class` vechi în `target`.

### Verificarea

Rulează toată suita, cu aplicația pornită:

```
mvn test
```

Toate cele 7 teste trebuie să treacă. Verifică apoi ruta de mobile, care trece acum prin dispatcher:

```
curl -i localhost:8083/mobile/products
curl -i -X POST localhost:8083/mobile/products
```

Primul request întoarce 200. Al doilea întoarce 405 în JSON.

**Ce se schimbă intenționat față de pasul 5:**

| Request | Pasul 5 | Pasul 6 |
|---|---|---|
| `GET /products/1/2` | 400, „Id must be a positive integer” | 404, ruta nu există |
| `POST /mobile/products` | 405, pagină HTML de la HttpServlet | 405, JSON |
| Momentul creării lui `MobileProductsController` | La pornire | La primul request |

### Corespondentul în Spring

| Pasul 6 | Spring |
|---|---|
| `@Controller("/products")` | `@RestController` plus `@RequestMapping("/products")` |
| `@Get("/{id}")` | `@GetMapping("/{id}")` |
| `@HttpMethod("GET")` pe `@Get` | `@RequestMapping(method = GET)` pe `@GetMapping` |
| `@PathVariable("id")` | `@PathVariable("id")` |
| `@ResponseStatus(201)` | `@ResponseStatus(HttpStatus.CREATED)` |
| Tabela construită de `registerController` | `RequestMappingHandlerMapping` |
| Lista explicită din Main | `@ComponentScan`, care descoperă clasele automat |

## 7. Capcane întâlnite

Toate capcanele de mai jos au apărut efectiv în timpul construirii acestui router.

1. **`invalid target release: 25`.** Maven rulează pe un JDK mai vechi decât cel cerut de pom. Maven folosește JDK-ul din `JAVA_HOME`, nu pe cel implicit al sistemului. Verifică cu `mvn -v`.

2. **Controllerul primește path-ul greșit, la pasul 1.** Cu router-ul mapat pe `/*`, `getPathInfo()` conține tot path-ul, nu doar id-ul. Fără `PathInfoRequest`, fiecare request cu id primește 400.

3. **`NoClassDefFoundError` pe macOS, la pasul 4.** Sistemul de fișiere implicit al macOS nu face diferența între litere mari și mici. Pentru `/mobileproducts`, convenția caută `MobileproductsController`. macOS găsește fișierul `MobileProductsController.class`, iar JVM-ul îl respinge, fiindcă numele din fișier nu se potrivește. Eroarea e un `Error`, nu o excepție, așa că nu o prinde nici `catch (ClassNotFoundException e)`, nici `GlobalExceptionFilter`. Rezultatul ar fi un 500. Pe Linux, problema nu apare.

4. **Clase compilate vechi în `target`, la pasul 5.** Maven nu șterge singur fișierele `.class` ale claselor eliminate din sursă. Convenția încarcă clase după nume, deci găsește și clasele care nu mai există în cod. Simptomul: `/product/1` întorcea 405 în loc de 404. Soluția este un build curat. E un risc real al oricărui mecanism care descoperă clase automat.

5. **Excepțiile învelite de reflection, la pasul 5.** `Method.invoke` învelește orice excepție aruncată de metodă într-o `InvocationTargetException`. Trebuie despachetată, ca filtrul global să vadă excepția originală.

6. **Recursivitatea în container, la pasul 3.** `computeIfAbsent` pe un `ConcurrentHashMap` nu permite modificarea aceluiași map în timpul calculului. Containerul se apelează recursiv, așa că folosește în schimb metode `synchronized`.

7. **Adnotare fără `@Retention(RetentionPolicy.RUNTIME)`, la pasul 6.** Retenția implicită a unei adnotări este `CLASS`: adnotarea ajunge în fișierul `.class`, dar reflection nu o vede. Uitată pe `@Controller`, pornirea se oprește cu „not annotated with @Controller”, deși adnotarea e acolo, în cod. Uitată pe o adnotare de verb, ruta pur și simplu nu apare, iar request-ul primește 404.

8. **Nume greșit în `@PathVariable`, la pasul 6.** Dacă scrii `@PathVariable("idd")` pe o rută cu `{id}`, eroarea apare abia la request, ca un 400 înșelător. Exercițiul 5 rezolvă problema.

## 8. Exerciții

1. **Adaugă o resursă nouă.** Creează `CategoriesController`, marcat cu `@Controller("/categories")`, cu o metodă `@Get` care întoarce categoriile. Pentru asta, adaugă o metodă de citire în `CategoryRepository` și una într-un serviciu. Înregistrează controllerul în Main. Compară cu pasul 4, când ruta ar fi apărut fără nicio linie în Main: ce ai câștigat și ce ai pierdut?
2. **Încearcă o dependență circulară.** Creează două clase care se cer reciproc în constructor și cere una dintre ele containerului. Citește mesajul de eroare.
3. **Treci la eager loading.** Spring creează implicit toate singleton-urile la pornire. Modifică `registerController` ca să ceară instanța containerului imediat. Gândește-te la ce câștigi și la ce pierzi.
4. **Adaugă antetul `Allow` la 405.** Standardul HTTP cere ca un răspuns 405 să spună ce verbe sunt permise. Strânge verbele rutelor al căror path s-a potrivit și pune-le în antet.
5. **Validează `@PathVariable` la pornire.** În `registerController`, verifică pentru fiecare parametru marcat cu `@PathVariable` că numele lui apare ca `{nume}` în șablon. Altfel, aruncă o excepție clară.
6. **Rezolvă conflictele de rute.** Adaugă în `ProductsController` o metodă `@Get("/search")`. Ruta `/products/search` se potrivește atunci și cu `/products/{id}`. Ordinea metodelor întoarse de `getMethods()` nu e garantată, deci câștigă oricare dintre ele. Sortează rutele astfel încât segmentele fixe să aibă prioritate față de cele variabile, cum face și Spring.
7. **Scanarea automată.** Înlocuiește lista din Main cu o metodă care parcurge folderul `target/classes`, încarcă fiecare clasă și o înregistrează dacă are `@Controller`. Testează apoi capcana 4: șterge un controller din sursă fără build curat și vezi ce se întâmplă.
