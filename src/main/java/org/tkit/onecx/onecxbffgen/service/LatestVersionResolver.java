package org.tkit.onecx.onecxbffgen.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class LatestVersionResolver {

    private static final String API_BASE = "https://api.github.com/repos/%s/releases/latest";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LatestVersionResolver() {
        this(HttpClient.newHttpClient(), new ObjectMapper());
    }

    LatestVersionResolver(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Resolves the latest release tag for a GitHub repository.
     *
     * @param ownerAndRepo  e.g. "onecx/onecx-quarkus3-parent"
     * @param fallback      version returned when GitHub is unreachable or returns no tag
     * @return resolved version string (leading 'v' stripped)
     */
    public String resolveLatest(String ownerAndRepo, String fallback) {
        try {
            String url = API_BASE.formatted(ownerAndRepo);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode node = objectMapper.readTree(response.body());
                String tag = node.path("tag_name").asText();
                if (tag != null && !tag.isBlank()) {
                    return normalize(tag);
                }
            }
        } catch (IOException | InterruptedException ignored) {
            if (ignored instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return fallback;
    }

    private String normalize(String raw) {
        return raw.trim().replaceFirst("^[vV]", "");
    }
}



