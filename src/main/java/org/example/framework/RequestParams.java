package org.example.framework;

import java.util.Map;

// parametrii din query string, fără ca controllerul să depindă de servlet API
public class RequestParams {

    private final Map<String, String[]> values;

    public RequestParams(Map<String, String[]> values) {
        this.values = values;
    }

    // primul "page" din "?page=2&size=10"; null dacă parametrul lipsește
    public String get(String name) {
        String[] parameterValues = values.get(name);
        if(parameterValues == null || parameterValues.length == 0){
            return null;
        }
        return parameterValues[0];
    }
}
