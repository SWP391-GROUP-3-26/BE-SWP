package com.swp391.beswp;

import com.swp391.beswp.entity.Role;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.service.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in HTTP checks against existing local SQL Server data. Never inserts or updates records. */
@EnabledIfSystemProperty(named = "member.read.integration", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberReadIntegrationTest {
    private static final String URL = "/api/receptionist/members";
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper mapper;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void realSearchMatchesEachExistingFieldCaseInsensitivelyAndTrims() throws Exception {
        for (String field : List.of("FullName", "Username", "Email", "Phone")) {
            var sample = jdbc.queryForList("select top 1 u.User_ID, u." + field + " as keyword from dbo.[User] u "
                    + "join dbo.Role r on r.Role_ID=u.Role_ID where lower(r.Role_Name)='member' "
                    + "and u." + field + " is not null and len(u." + field + ")>0 order by u.User_ID");
            assertFalse(sample.isEmpty(), "Local database needs a member with field: " + field);
            String keyword = sample.getFirst().get("keyword").toString();
            int id = ((Number) sample.getFirst().get("User_ID")).intValue();
            for (String variant : List.of(keyword.toLowerCase(Locale.ROOT), "  " + keyword.toUpperCase(Locale.ROOT) + "  ")) {
                JsonNode result = search(variant, 0, 100);
                boolean found = false;
                for (JsonNode item : result.path("data")) {
                    assertEquals("member", item.path("role").asText().toLowerCase(Locale.ROOT));
                    assertSafe(item);
                    found |= item.path("userId").asInt() == id;
                }
                assertTrue(found, "Search should include the selected member for " + field);
            }
        }
    }

    @Test
    void emptyNoMatchAndLiteralWildcardsDoNotReturnAllMembers() throws Exception {
        for (String keyword : List.of("", "   ", UUID.randomUUID().toString())) {
            JsonNode result = search(keyword, 0, 20);
            assertEquals(0, result.path("total").asInt());
            assertEquals(0, result.path("data").size());
        }
        for (String keyword : List.of("%", "_", "[")) {
            long expected = jdbc.queryForObject("select count(*) from dbo.[User] u join dbo.Role r on r.Role_ID=u.Role_ID "
                    + "where lower(r.Role_Name)='member' and (charindex(?,u.FullName)>0 or charindex(?,u.Username)>0 "
                    + "or charindex(?,u.Email)>0 or charindex(?,u.Phone)>0)", Long.class, keyword, keyword, keyword, keyword);
            assertEquals(expected, search(keyword, 0, 20).path("total").asLong());
        }
    }

    @Test
    void paginationAndRoleFilteringUseRealDatabase() throws Exception {
        long expected = jdbc.queryForObject("select count(*) from dbo.[User] u join dbo.Role r on r.Role_ID=u.Role_ID "
                + "where lower(r.Role_Name)='member' and (charindex('@',u.FullName)>0 or charindex('@',u.Username)>0 "
                + "or charindex('@',u.Email)>0 or charindex('@',u.Phone)>0)", Long.class);
        assertTrue(expected >= 2, "Local database needs two members with searchable email addresses");
        JsonNode first = search("@", 0, 1);
        JsonNode second = search("@", 1, 1);
        assertEquals(expected, first.path("total").asLong());
        assertEquals(expected, second.path("total").asLong());
        assertEquals(1, first.path("data").size());
        assertEquals(1, second.path("data").size());
        assertTrue(first.path("data").get(0).path("userId").asInt() < second.path("data").get(0).path("userId").asInt());
        assertEquals(0, search("@", (int) expected, 1).path("data").size());
    }

    @Test
    void detailOnlyReturnsExistingMembersAndSafeFields() throws Exception {
        var accounts = jdbc.queryForList("select u.User_ID, r.Role_Name from dbo.[User] u left join dbo.Role r on r.Role_ID=u.Role_ID");
        assertFalse(accounts.isEmpty());
        for (var account : accounts) {
            boolean member = "member".equalsIgnoreCase(String.valueOf(account.get("Role_Name")));
            var response = get(URL + "/" + account.get("User_ID"), token("Receptionist"));
            assertEquals(member ? 200 : 404, response.statusCode());
            if (member) {
                var data = mapper.readTree(response.body()).path("data");
                assertEquals(((Number) account.get("User_ID")).intValue(), data.path("userId").asInt());
                assertSafe(data);
            }
        }
        assertEquals(404, get(URL + "/-1", token("Receptionist")).statusCode());
        for (String suffix : List.of("/abc", "/2147483648", "?page=-1", "?size=101")) {
            assertEquals(400, get(URL + suffix, token("Receptionist")).statusCode());
        }
    }

    @Test
    void realHttpJwtSecurityRejectsAnonymousInvalidTokensAndOtherRoles() throws Exception {
        for (String path : List.of(URL, URL + "/-1")) {
            for (String token : List.of("", "invalid")) {
                var response = get(path, token);
                assertEquals(401, response.statusCode());
                assertEquals("Unauthorized", mapper.readTree(response.body()).path("message").asText());
            }
            for (String role : List.of("Member", "Admin", "Coach", "Center Manager")) {
                var response = get(path, token(role));
                assertEquals(403, response.statusCode());
                assertEquals("Forbidden", mapper.readTree(response.body()).path("message").asText());
            }
        }
    }

    private JsonNode search(String keyword, int page, int size) throws Exception {
        var response = get(URL + "?keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                + "&page=" + page + "&size=" + size, token("Receptionist"));
        assertEquals(200, response.statusCode());
        return mapper.readTree(response.body());
    }

    private void assertSafe(JsonNode data) {
        assertEquals(11, data.size());
        for (String field : List.of("password", "passwordHash", "accessToken", "refreshToken", "secret")) {
            assertFalse(data.has(field));
        }
    }

    private String token(String roleName) {
        // Test identity only; actual JWT signing/filter and SQL-backed endpoints are used.
        Role role = new Role();
        role.setRoleName(roleName);
        User identity = new User();
        identity.setId(-1);
        identity.setRole(role);
        return jwt.generateToken(identity);
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (!token.isEmpty()) request.header("Authorization", "Bearer " + token);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
