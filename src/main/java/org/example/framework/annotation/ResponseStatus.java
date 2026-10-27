package org.example.framework.annotation; // [router-6] adnotările framework-ului, separate de restul codului de framework

import java.lang.annotation.ElementType; // [router-6] tipurile de elemente pe care se poate pune o adnotare
import java.lang.annotation.Retention; // [router-6] cât timp trăiește adnotarea
import java.lang.annotation.RetentionPolicy; // [router-6] valorile posibile pentru Retention
import java.lang.annotation.Target; // [router-6] unde are voie să stea adnotarea

@Retention(RetentionPolicy.RUNTIME) // [router-6] adnotarea trebuie să existe la runtime, ca dispatcher-ul să o citească
@Target(ElementType.METHOD) // [router-6] se pune pe metodele controllerului
public @interface ResponseStatus { // [router-6] statusul HTTP pentru un răspuns reușit; fără ea, statusul este 200; la fel ca în Spring
    int value(); // [router-6] de exemplu 201 pentru create
} // [router-6] sfârșitul adnotării ResponseStatus
