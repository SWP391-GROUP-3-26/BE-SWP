package com.swp391.beswp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SwaggerIntegrationTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void swaggerIsPublicAndDocumentsCurrentControllers() throws Exception {
        var ui = get("/swagger-ui/index.html");
        assertEquals(200, ui.statusCode());
        assertTrue(ui.body().contains("Swagger UI"));
        assertEquals(200, get("/swagger-ui/swagger-ui-bundle.js").statusCode());
        assertEquals(302, get("/swagger-ui.html").statusCode());

        var docs = get("/v3/api-docs");
        assertEquals(200, docs.statusCode());
        var document = mapper.readTree(docs.body());
        assertTrue(document.path("openapi").asText().startsWith("3."));
        var paths = document.path("paths");
        for (String path : new String[]{"/api/auth/login", "/api/auth/register",
                "/api/auth/google/exchange", "/api/receptionist/members", "/api/receptionist/members/{id}", "/api/subjects", "/api/subjects/{id}"}) {
            assertTrue(paths.has(path), "Missing documented endpoint: " + path);
        }
        var search = paths.path("/api/receptionist/members").path("get");
        assertTrue(search.path("responses").has("200"));
        assertEquals(3, search.path("parameters").size());
        assertTrue(search.path("security").get(0).has("bearerAuth"));
        assertTrue(paths.path("/api/receptionist/members/{id}").path("get").path("responses").has("404"));
        assertEquals("http", document.path("components").path("securitySchemes").path("bearerAuth").path("type").asText());
        var config = get("/v3/api-docs/swagger-config");
        assertEquals(200, config.statusCode());
        assertEquals("/v3/api-docs", mapper.readTree(config.body()).path("url").asText());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
