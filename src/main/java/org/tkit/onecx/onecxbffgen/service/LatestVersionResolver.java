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

    public enum Source {
        LATEST,
        DEFAULT
    }

    public record ResolvedVersion(String version, Source source) {
    }

    /**
     * Resolves the latest release tag for a GitHub repository.
     *
     * @param ownerAndRepo  e.g. "onecx/onecx-quarkus3-parent"
     * @param fallback      version returned when GitHub is unreachable or returns no tag
     * @return resolved version string (leading 'v' stripped)
     */
    public String resolveLatest(String ownerAndRepo, String fallback) {
        return resolveLatest(ownerAndRepo, fallback, null);
    }

    public String resolveLatest(String ownerAndRepo, String fallback, String githubToken) {
        return resolveLatestWithSource(ownerAndRepo, fallback, githubToken).version();
    }

    public ResolvedVersion resolveLatestWithSource(String ownerAndRepo, String fallback) {
        return resolveLatestWithSource(ownerAndRepo, fallback, null);
    }

    public ResolvedVersion resolveLatestWithSource(String ownerAndRepo, String fallback, String githubToken) {
        try {
            String url = API_BASE.formatted(ownerAndRepo);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28");

            String effectiveToken = resolveToken(githubToken);
            if (effectiveToken != null && !effectiveToken.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + effectiveToken);
            }

            HttpRequest request = requestBuilder.GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode node = objectMapper.readTree(response.body());
                String tag = node.path("tag_name").asText();
                if (tag != null && !tag.isBlank()) {
                    return new ResolvedVersion(normalize(tag), Source.LATEST);
                }
            } else if (response.statusCode() == 403) {
                System.err.println("⚠ GitHub API rate limit (403) for " + ownerAndRepo
                        + ". Pass --github-token (or set GITHUB_TOKEN) to increase limit to 5000 req/hour.");
            } else {
                System.err.println("⚠ GitHub API returned status " + response.statusCode() + " for " + ownerAndRepo);
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("⚠ Failed to resolve latest version for " + ownerAndRepo + ": " + e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return new ResolvedVersion(fallback, Source.DEFAULT);
    }

    private String normalize(String raw) {
        return raw.trim().replaceFirst("^[vV]", "");
    }

    private String resolveToken(String explicitToken) {
        if (explicitToken != null && !explicitToken.isBlank()) {
            return explicitToken;
        }
        return System.getenv("GITHUB_TOKEN");
    }
}
