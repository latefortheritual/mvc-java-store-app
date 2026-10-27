package org.example.framework.annotation; // [router-6] adnotările framework-ului, separate de restul codului de framework

import java.lang.annotation.ElementType; // [router-6] tipurile de elemente pe care se poate pune o adnotare
import java.lang.annotation.Retention; // [router-6] cât timp trăiește adnotarea
import java.lang.annotation.RetentionPolicy; // [router-6] valorile posibile pentru Retention
import java.lang.annotation.Target; // [router-6] unde are voie să stea adnotarea

@Retention(RetentionPolicy.RUNTIME) // [router-6] adnotarea trebuie să existe la runtime, ca dispatcher-ul să o citească
@Target(ElementType.TYPE) // [router-6] se pune pe clasă
public @interface Controller { // [router-6] marchează un controller și îi dă path-ul de bază; în Spring: @RestController plus @RequestMapping("/products")
    String value(); // [router-6] path-ul de bază, de exemplu "/products"
} // [router-6] sfârșitul adnotării Controller
