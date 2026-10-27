package org.example.framework.annotation; // [router-6] adnotările framework-ului, separate de restul codului de framework

import java.lang.annotation.ElementType; // [router-6] tipurile de elemente pe care se poate pune o adnotare
import java.lang.annotation.Retention; // [router-6] cât timp trăiește adnotarea
import java.lang.annotation.RetentionPolicy; // [router-6] valorile posibile pentru Retention
import java.lang.annotation.Target; // [router-6] unde are voie să stea adnotarea

@HttpMethod("PATCH") // [router-6] meta-adnotarea îi spune dispatcher-ului că @Patch înseamnă verbul PATCH
@Retention(RetentionPolicy.RUNTIME) // [router-6] adnotarea trebuie să existe la runtime, ca dispatcher-ul să o citească
@Target(ElementType.METHOD) // [router-6] se pune pe metodele controllerului
public @interface Patch { // [router-6] echivalentul lui @PatchMapping din Spring; exemplu: PATCH /products/{id}
    String value() default ""; // [router-6] path-ul relativ la @Controller; gol înseamnă chiar path-ul controllerului
} // [router-6] sfârșitul adnotării Patch
