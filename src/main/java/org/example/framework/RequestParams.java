package org.example.framework; // [router-5] ține de framework: controllerul primește parametrii fără să știe de HttpServletRequest

import java.util.Map; // [router-5] tipul în care servletul ține parametrii din query string

public class RequestParams { // [router-5] parametrii din query string, de exemplu ?page=2&size=10

    private final Map<String, String[]> values; // [router-5] un parametru poate apărea de mai multe ori, deci fiecare are o listă de valori

    public RequestParams(Map<String, String[]> values) { // [router-5] primește un map simplu, ca un test să-l poată construi fără mock-uri
        this.values = values; // [router-5] păstrează valorile
    } // [router-5] sfârșitul constructorului

    public String get(String name) { // [router-5] întoarce prima valoare a parametrului, sau null dacă lipsește
        String[] parameterValues = values.get(name); // [router-5] toate valorile parametrului cu numele dat
        if (parameterValues == null || parameterValues.length == 0) { // [router-5] parametrul lipsește din URL
            return null; // [router-5] la fel ca request.getParameter() pentru un parametru lipsă
        } // [router-5] sfârșitul verificării
        return parameterValues[0]; // [router-5] prima valoare, la fel ca request.getParameter()
    } // [router-5] sfârșitul metodei get
} // [router-5] sfârșitul clasei RequestParams
