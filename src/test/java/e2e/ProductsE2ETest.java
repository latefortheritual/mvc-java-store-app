package e2e;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProductsE2ETest {

    private static final String BASE_URL = "http://localhost:8083";
    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        return client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    @Test
    void negativePageReturnsSameAsDefaultPage() throws IOException, InterruptedException {
        HttpResponse<String> negativePageResponse = get(BASE_URL + "/product?page=-1&size=10");
        HttpResponse<String> defaultPageResponse = get(BASE_URL + "/product?size=10");

        assertEquals(200, negativePageResponse.statusCode());
        assertEquals(200, defaultPageResponse.statusCode());

        assertEquals(defaultPageResponse.body(), negativePageResponse.body());

    }


    @Test
    void textPageReturnsSameAsDefaultPage() throws IOException, InterruptedException {
        HttpResponse<String> textPageResponse = get(BASE_URL + "/product?page=abc&size=10");
        HttpResponse<String> defaultPageResponse = get(BASE_URL + "/product?size=10");

        assertEquals(200, textPageResponse.statusCode());
        assertEquals(200, defaultPageResponse.statusCode());

        assertEquals(defaultPageResponse.body(), textPageResponse.body());
    }

    @Test
    void zeroSizeReturnsSameAsDefaultSize() throws IOException, InterruptedException {
        HttpResponse<String> zeroSizeResponse = get(BASE_URL + "/product?size=0");
        HttpResponse<String> defaultSizeResponse = get(BASE_URL + "/product");

        assertEquals(200, zeroSizeResponse.statusCode());
        assertEquals(200, defaultSizeResponse.statusCode());

        assertEquals(defaultSizeResponse.body(), zeroSizeResponse.body());
    }

    @Test
    void sizeGreaterThanMaxReturnsSameAsMaxSize() throws IOException, InterruptedException {
        HttpResponse<String> sizeGreaterThanMaxResponse = get(BASE_URL + "/product?size=1001");
        HttpResponse<String> maxSizeResponse = get(BASE_URL + "/product?size=1000");

        assertEquals(200, sizeGreaterThanMaxResponse.statusCode());
        assertEquals(200, maxSizeResponse.statusCode());

        assertEquals(maxSizeResponse.body(), sizeGreaterThanMaxResponse.body());
    }

    @Test
    void missingParamsUseDefaultValues() throws IOException, InterruptedException {
        HttpResponse<String> missingParamsResponse = get(BASE_URL + "/product");
        HttpResponse<String> DefaultValuesResponse = get(BASE_URL + "/product?page=1&size=100");

        assertEquals(200, missingParamsResponse.statusCode());
        assertEquals(200, DefaultValuesResponse.statusCode());

        assertEquals(DefaultValuesResponse.body(), missingParamsResponse.body());
    }

}

//zeroPageReturnsSameAsDefaultPage
//negativeSizeReturnsSameAsDefaultSize
//textSizeReturnsSameAsDefaultSize
//mixedPageReturnsSameAsDefaultPage
//responseDoesNotContainMoreTicketsThanRequestedSize