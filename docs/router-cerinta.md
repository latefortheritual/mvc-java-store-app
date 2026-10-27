# Router: de la cod manual la cod bazat pe convenție

## Scop

Scopul este didactic. Construim de mână mecanismul pe care Spring îl ascunde: un router care pornește de la URL, găsește controllerul potrivit, îl instanțiază cu toate dependențele și apelează metoda corespunzătoare verbului HTTP. Trecem astfel de la cod scris manual, cu if-else, la cod bazat pe convenție.

## Cerința tradusă

1. **Un singur punct de intrare primește toate request-urile.** Din `GET /products/123` extrage verbul `GET`, resursa `products` și id-ul `123`.
2. **Din numele resursei află clasa controllerului.** Numele resursei se capitalizează și se pune într-un șablon de string, adică `String.format` cu placeholder-ul `%s`. Rezultatul este numele complet al clasei, de exemplu `org.example.controller.ProductsController`.
3. **Din numele clasei obține o instanță.** Java nu poate face `new` pe o variabilă de tip String. Există două căi: o listă pregătită dinainte, adică un Factory, sau Reflection.
4. **Distincția dintre definiția clasei și instanță.** `Class.forName` întoarce definiția clasei, un obiect `Class<?>` cu metadate despre constructori și metode. Instanța apare abia când constructorul este apelat prin reflection.
5. **Instanța se creează lazy și primește automat dependențele.** Se creează la primul request, iar serviciul și repository-urile i se injectează singure. Acesta este containerul de dependency injection.
6. **Pe instanță se apelează metoda potrivită verbului.** Primul pas este `service(request, response)`, metoda publică prin care HttpServlet ajunge singur la `doGet`, `doPost` și celelalte. Pasul final este un controller propriu, detașat de HttpServlet, cu metode ca `getAll` și `getOne`.
7. **Operațiile CRUD trebuie să funcționeze în continuare.** Asta include și PATCH. Ruta `/mobile/products` se tratează separat.

## Corespondența cu Spring

| Din notele inițiale | În Java | În Spring |
|---|---|---|
| Router-ul care pornește de la URL | Un servlet unic mapat pe `/*` | DispatcherServlet |
| Lista „Products indică spre controller” | Un map de la nume la Supplier | HandlerMapping |
| „Dă-mi instanța și injectează tot” | Constructor apelat prin reflection, cu cache | ApplicationContext |
| „Apelează în mod magic doCeva” | `service()`, apoi `Method.invoke` | HandlerAdapter |
| ProductHttp cu getAll și getOne | O clasă simplă, fără HttpServlet | @RestController |

## Product versus products

- **URL-urile sunt la plural,** conform convenției REST: `/products` și `/products/123`.
- **Ruby on Rails numește controllerele tot la plural,** de exemplu `ProductsController`, iar modelele la singular, `Product`. Rails este framework-ul care a popularizat ideea de convenție înaintea configurării.
- **Spring nu impune nicio convenție de nume.** Ruta se declară explicit cu o adnotare.
- **Concluzie:** cu convenția Rails, capitalizarea numelui din URL funcționează direct, fără reguli de singularizare. `categories` devine `CategoriesController`.

## Decizii luate

1. **Router-ul este un servlet unic mapat pe `/*`.** Filtrele de logging și de excepții rămân neschimbate și se aplică înaintea lui.
2. **Pornim cu varianta manuală, cu if-else,** ca un pas scurt. Arată clar ce anume înlocuiește mecanismul automat.
3. **Controllerul se găsește în două etape.** Întâi un registry, adică lista pregătită dinainte. Apoi reflection pe convenția de nume. Adnotările rămân opționale, pentru final.
4. **Controllerele se numesc după convenția Rails:** numele resursei din URL plus `Controller`, deci `ProductsController`.
5. **Un singur controller per resursă.** Acum `/products` și `/products/123` sunt servite de două clase diferite. Le unim în pasul în care controllerul se detașează de HttpServlet.
6. **Metodele controllerului propriu urmează tabelul de mai jos.**

   | Request | Metodă |
   |---|---|
   | `GET /products` | `getAll` |
   | `GET /products/123` | `getOne` |
   | `POST /products` | `create` |
   | `PUT /products/123` | `update` |
   | `PATCH /products/123` | `patch` |
   | `DELETE /products/123` | `delete` |

7. **Metodele primesc parametri curați și întorc obiecte.** Primesc id-ul ca `int` și body-ul ca DTO. Dispatcher-ul face partea de HTTP: parsează id-ul, citește și scrie JSON-ul. Asta elimină și duplicarea din controllere.
8. **Containerul folosește constructor injection prin reflection.** Instanțele se creează lazy și se păstrează ca singleton. Repository-urile trebuie să primească `DatabaseConfig` în constructor, nu trei String-uri, fiindcă un container nu poate ghici ce String să injecteze.
9. **`/mobile/products` rămâne deocamdată pe maparea ei din Tomcat.** O mapare exactă câștigă în fața lui `/*`. Ruta se integrează în router abia la final. La pasul 6, ruta a intrat în router: `MobileProductsController` e marcat cu `@Controller("/mobile/products")`, iar maparea din Tomcat a dispărut.
10. **Erorile router-ului sunt excepții tratate în `GlobalExceptionFilter`.** 404 pentru o resursă necunoscută, 405 pentru un verb nesuportat, 400 pentru un id invalid.
11. **Codul de framework stă într-un pachet separat, `org.example.framework`.**
12. **Pasul 6 folosește varianta 6a: adnotări, cu lista de controllere dată explicit în Main.** Scanarea automată a pachetului, varianta 6b, rămâne exercițiu. Detaliile variantei 6a:
    - **Adnotările stau într-un sub-pachet,** `org.example.framework.annotation`.
    - **Adnotările de verb sunt marcate cu meta-adnotarea `@HttpMethod`,** la fel ca `@GetMapping` în Spring. Dispatcher-ul află verbul fără un if-else pe tipul adnotării.
    - **Variabilele din path se leagă cu `@PathVariable`,** iar statusul 201 vine din `@ResponseStatus(201)`.
    - **Tabela de rute se construiește la pornire,** dar controllerele rămân lazy.
    - **Un path inexistent dă 404, iar un path existent cu alt verb dă 405.** De aceea `/products/1/2` dă acum 404, nu 400.

## De ce un pachet `framework`

- **Codul de aplicație știe ce e un produs.** Controllerele, serviciul, repository-urile și DTO-urile se schimbă când se schimbă regulile magazinului.
- **Codul de framework nu știe ce e un produs.** Router-ul și containerul știu doar despre HTTP, path-uri, clase și constructori.
- **Așa e construit și Spring.** Framework-ul stă într-o bibliotecă separată. Aplicația depinde de el, dar el nu știe nimic despre aplicație.
- **Regula:** aplicația are voie să folosească `framework`, iar `framework` nu are voie să depindă de aplicație. Testul simplu este că pachetul ar trebui să funcționeze neschimbat într-un alt proiect.
- **La pasul 1, regula încă nu e respectată.** Fiecare import din pachetele aplicației în `framework` este o legătură care urmează să fie eliminată în pașii următori.
- **După pasul 6, a rămas o singură legătură:** dispatcher-ul importă trei excepții din `org.example.exception`, ca `GlobalExceptionFilter` să le transforme în statusuri HTTP. Containerul, adnotările și `RequestParams` nu depind deloc de aplicație.

## Planul pe pași

Fiecare pas este un commit care funcționează. Testele E2E și comenzile `curl` confirmă după fiecare pas că API-ul se comportă la fel.

1. **Front controller manual.** Un servlet pe `/*` face if-else pe resursă și apelează `service()` pe servleturile existente. Din Main dispar mapările de produse.
2. **Registry.** If-else-ul devine un map de la numele resursei la un Supplier. Instanța se creează lazy, la primul request. Dispatcher-ul nu mai conține string-ul „products”.
3. **Container DI.** Main nu mai construiește manual lanțul de dependențe. Containerul îl construiește prin reflection. Dispatcher-ul cere instanțele containerului.
4. **Convenția de nume.** Registry-ul manual dispare. Numele clasei se calculează din URL și se încarcă prin reflection.
5. **Controller propriu.** `ProductsController` nu mai extinde HttpServlet și primește cele șase metode. Dispatcher-ul le alege după tabelul de la decizia 6 și le apelează prin `Method.invoke`.
6. **Adnotări.** `@Controller` pe clasă declară path-ul de bază, iar `@Get`, `@Post`, `@Put`, `@Patch` și `@Delete` declară rutele. Convenția de nume și tabelul de acțiuni dispar, iar ruta de mobile intră și ea în router.

Ghidul pas cu pas pentru reproducerea tuturor celor șase pași este în `docs/router-ghid-pas-cu-pas.md`.

## Starea punctelor din code review

Punctele 1, 2 și 3 au fost corectate înainte de router. Rutarea a rezolvat alte trei puncte:

- **Punctul 4, rezolvat la pasul 5:** un id invalid răspunde acum cu „Id must be a positive integer”, nu cu „Invalid request body”.
- **Punctul 5, rezolvat la pasul 5:** parsarea id-ului, validarea și scrierea JSON stau acum o singură dată, în dispatcher.
- **Punctul 6, rezolvat la pasul 5:** `sendError` a dispărut din controllere.

Tot la pasul 5 a fost reparat și testul unitar care pica înainte de router.

Punctele de mai jos rămân deschise:

- **Punctul 7:** request-urile folosesc `brandId`, iar răspunsurile `brand_id`.
- **Punctul 8:** mesajele de validare a prețului nu corespund regulii din schema bazei de date.
- **Punctul 9:** erorile de validare din DTO-uri își pierd mesajul și ajung la client ca un 400 generic.
- **Punctul 10, rezolvat parțial la pasul 3:** repository-urile primesc acum `DatabaseConfig`, nu trei String-uri. Rămân deschise conexiunea nouă deschisă la fiecare query și cele două query-uri care nu închid ResultSet-ul în try-with-resources.
