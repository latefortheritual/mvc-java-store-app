package org.example.framework; // [router-3] containerul ține de framework: nu știe nimic despre produse

import java.lang.reflect.Constructor; // [router-3] definiția unui constructor, citită prin reflection
import java.util.HashMap; // [router-3] implementarea map-ului de instanțe
import java.util.HashSet; // [router-3] implementarea setului de clase aflate în construcție
import java.util.Map; // [router-3] tipul pentru map-ul de instanțe
import java.util.Set; // [router-3] tipul pentru setul de clase aflate în construcție

public class Container { // [router-3] containerul de dependency injection, echivalentul ApplicationContext din Spring

    private final Map<Class<?>, Object> instances = new HashMap<>(); // [router-3] singleton-urile: câte o singură instanță pentru fiecare clasă
    private final Set<Class<?>> inProgress = new HashSet<>(); // [router-3] clasele aflate acum în construcție, ca să detectăm dependențele circulare

    public synchronized <T> void register(Class<T> type, T instance) { // [router-3] pentru obiecte pe care containerul nu le poate construi singur, ca DatabaseConfig
        instances.put(type, instance); // [router-3] instanța dată devine singleton-ul pentru clasa respectivă
    } // [router-3] sfârșitul metodei register

    public synchronized <T> T get(Class<T> type) { // [router-3] „dă-mi instanța”; synchronized, ca două request-uri simultane să nu creeze două instanțe
        Object existing = instances.get(type); // [router-3] căutăm întâi în singleton-uri
        if (existing != null) { // [router-3] instanța există deja
            return type.cast(existing); // [router-3] o refolosim
        } // [router-3] sfârșitul verificării de cache

        if (inProgress.contains(type)) { // [router-3] clasa e deja în construcție mai sus pe stivă: A are nevoie de B, iar B de A
            throw new IllegalStateException("Circular dependency on " + type.getName()); // [router-3] eroare clară, în loc de StackOverflowError
        } // [router-3] sfârșitul verificării de dependență circulară

        inProgress.add(type); // [router-3] marcăm clasa ca aflată în construcție
        try { // [router-3] try/finally: scoatem marcajul chiar dacă construcția eșuează
            T created = create(type); // [router-3] lazy: instanța se creează abia acum, la prima cerere
            instances.put(type, created); // [router-3] o păstrăm ca singleton pentru cererile viitoare
            return created; // [router-3] întoarcem instanța nou creată
        } finally { // [router-3] se execută și la succes, și la eroare
            inProgress.remove(type); // [router-3] clasa nu mai e în construcție
        } // [router-3] sfârșitul blocului try/finally
    } // [router-3] sfârșitul metodei get

    private <T> T create(Class<T> type) { // [router-3] construiește o instanță nouă prin reflection
        Constructor<?>[] constructors = type.getConstructors(); // [router-3] definiția clasei ne dă lista constructorilor publici
        if (constructors.length != 1) { // [router-3] cu zero sau mai mulți constructori, containerul nu știe pe care să îl aleagă
            throw new IllegalStateException(type.getName() + " must have exactly one public constructor"); // [router-3] regula containerului, spusă explicit
        } // [router-3] sfârșitul verificării constructorului

        Constructor<?> constructor = constructors[0]; // [router-3] singurul constructor public
        Class<?>[] parameterTypes = constructor.getParameterTypes(); // [router-3] tipurile parametrilor sunt dependențele clasei
        Object[] arguments = new Object[parameterTypes.length]; // [router-3] aici strângem valorile pentru fiecare parametru
        for (int i = 0; i < parameterTypes.length; i++) { // [router-3] pentru fiecare dependență
            arguments[i] = get(parameterTypes[i]); // [router-3] recursiv: containerul își rezolvă singur dependențele dependențelor
        } // [router-3] sfârșitul buclei de dependențe

        try { // [router-3] apelul prin reflection poate arunca excepții verificate
            return type.cast(constructor.newInstance(arguments)); // [router-3] definiția clasei devine instanță: echivalentul lui new, apelat dinamic
        } catch (ReflectiveOperationException e) { // [router-3] constructorul a eșuat sau nu poate fi apelat
            throw new IllegalStateException("Could not create " + type.getName(), e); // [router-3] o transformăm în excepție runtime, cu cauza originală
        } // [router-3] sfârșitul blocului try/catch
    } // [router-3] sfârșitul metodei create
} // [router-3] sfârșitul clasei Container
