package org.example.framework.annotation; // [router-6] adnotările framework-ului, separate de restul codului de framework

import java.lang.annotation.ElementType; // [router-6] tipurile de elemente pe care se poate pune o adnotare
import java.lang.annotation.Retention; // [router-6] cât timp trăiește adnotarea
import java.lang.annotation.RetentionPolicy; // [router-6] valorile posibile pentru Retention
import java.lang.annotation.Target; // [router-6] unde are voie să stea adnotarea

@Retention(RetentionPolicy.RUNTIME) // [router-6] fără RUNTIME, compilatorul șterge adnotarea, iar reflection nu o mai vede
@Target(ElementType.ANNOTATION_TYPE) // [router-6] meta-adnotare: stă doar pe alte adnotări, de exemplu pe @Get
public @interface HttpMethod { // [router-6] spune ce verb HTTP reprezintă o adnotare; în Spring, @GetMapping are pe ea @RequestMapping(method = GET)
    String value(); // [router-6] verbul, de exemplu "GET"
} // [router-6] sfârșitul adnotării HttpMethod
