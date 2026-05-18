package org.tkit.onecx.onecxbffgen.service;
import org.tkit.onecx.onecxbffgen.model.DependencyProfile;
import org.tkit.onecx.onecxbffgen.model.OperationModel;
import org.tkit.onecx.onecxbffgen.model.SchemaModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class ProjectWriter {
    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile("\\{([^}/]+)}");
    private static final String BACKEND_CONFIG_KEY = "backend_api";
    private final TemplateRenderer templateRenderer;
    public ProjectWriter() {
        this(new TemplateRenderer());
    }
    ProjectWriter(TemplateRenderer templateRenderer) {
        this.templateRenderer = templateRenderer;
    }
    public void writePom(Path projectDir,
                         String projectName,
                         String groupId,
                         String artifactId,
                         String parentVersion,
                         DependencyProfile profile,
                         String basePackage,
                         String frontendFileName) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("projectName", projectName);
        values.put("parentVersion", parentVersion);
        values.put("groupId", groupId);
        values.put("artifactId", artifactId);
        values.put("packaging", profile == DependencyProfile.MODERN_3_1_PLUS ? "    <packaging>quarkus</packaging>" : "");
        values.put("javaVersion", javaVersion(profile));
        values.put("junitArtifact", usesLegacyJunitArtifacts(profile) ? "quarkus-junit5" : "quarkus-junit");
        values.put("junitMockitoArtifact",
                usesLegacyJunitArtifacts(profile) ? "quarkus-junit5-mockito" : "quarkus-junit-mockito");
        values.put("mockserverSwaggerParserExclusion", profile == DependencyProfile.LEGACY_UP_TO_2_5
                ? "            <exclusions>\n                <exclusion>\n                    <groupId>io.swagger.parser.v3</groupId>\n                    <artifactId>swagger-parser</artifactId>\n                </exclusion>\n            </exclusions>\n"
                : "");
        values.put("legacySwaggerParser", profile == DependencyProfile.LEGACY_UP_TO_2_5
                ? "        <dependency>\n            <groupId>io.swagger.parser.v3</groupId>\n            <artifactId>swagger-parser</artifactId>\n            <scope>test</scope>\n        </dependency>\n"
                : "");
        values.put("openApiJavaOption", profile == DependencyProfile.MODERN_3_1_PLUS ? "" : "                        <java17>true</java17>");
        values.put("frontendApiFileName", frontendFileName);
        values.put("internalApiPackage", "gen." + basePackage + ".rs.internal");
        values.put("internalModelPackage", "gen." + basePackage + ".rs.internal.model");
        writeTemplate(projectDir.resolve("pom.xml"), "bff-project/pom.xml.tpl", values);
    }
    public void writeGeneratedReadme(Path projectDir,
                                     String projectName,
                                     String groupId,
                                     String basePackage,
                                     String parentVersion,
                                     DependencyProfile profile) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("projectName", projectName);
        values.put("groupId", groupId);
        values.put("basePackage", basePackage);
        values.put("parentVersion", parentVersion);
        values.put("dependencyProfile", profile.name());
        values.put("javaVersion", javaVersion(profile));
        writeTemplate(projectDir.resolve("README.md"), "bff-project/README.md.tpl", values);
    }
    public void writeApplicationFiles(Path projectDir,
                                      String projectName,
                                      String groupId,
                                      String basePackage,
                                      String artifactId,
                                      String backendFileName) throws IOException {
        Map<String, String> appValues = new LinkedHashMap<>();
        appValues.put("projectName", projectName);
        appValues.put("groupId", groupId);
        appValues.put("basePackage", basePackage);
        appValues.put("backendSpecKey", toPropertyToken(backendFileName));
        appValues.put("backendClientBasePackage", "gen." + basePackage + ".backend.client");
        appValues.put("backendConfigKey", BACKEND_CONFIG_KEY);
        writeTemplate(projectDir.resolve("src/main/resources/application.properties"),
                "bff-project/application.properties.tpl", appValues);
        writeTemplate(projectDir.resolve(".gitignore"), "bff-project/gitignore.tpl", Map.of());
        writeTemplate(projectDir.resolve("src/main/helm/Chart.yaml"), "bff-project/Chart.yaml.tpl",
                Map.of("artifactId", artifactId, "projectName", projectName));
        writeTemplate(projectDir.resolve("src/main/helm/values.yaml"), "bff-project/values.yaml.tpl",
                Map.of("artifactId", artifactId,
                        "projectName", projectName,
                        "permissionKey", defaultPermissionKey(artifactId),
                        "defaultScopes", "ocx-" + defaultPermissionKey(artifactId) + ":all, ocx-pm:read"));
        writeTemplate(projectDir.resolve("src/main/docker/Dockerfile.jvm"), "bff-project/Dockerfile.jvm.tpl", Map.of());
        writeTemplate(projectDir.resolve("src/main/docker/Dockerfile.native"), "bff-project/Dockerfile.native.tpl", Map.of());
    }
    public void writeWorkflowFiles(Path projectDir, DependencyProfile profile) throws IOException {
        String jv = javaVersion(profile);
        String content = "name: build-branch\n" +
                "on:\n" +
                "  push:\n" +
                "    branches-ignore:\n" +
                "      - main\n" +
                "jobs:\n" +
                "  build_branch:\n" +
                "    runs-on: ubuntu-latest\n" +
                "    steps:\n" +
                "      - uses: actions/checkout@v4\n" +
                "      - uses: actions/setup-java@v4\n" +
                "        with:\n" +
                "          distribution: temurin\n" +
                "          java-version: '" + jv + "'\n" +
                "      - name: Build\n" +
                "        run: mvn -B -ntp clean verify\n";
        Path target = projectDir.resolve(".github/workflows/build-branch.yml");
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }
    public void writeControllerClasses(Path projectDir,
                                       String pkg,
                                       Map<String, List<OperationModel>> controllers,
                                       Map<String, String> backendClientByController,
                                       boolean implementFrontendApi,
                                       boolean todoStubMode) throws IOException {
        Path baseDir = projectDir.resolve("src/main/java/" + pkg.replace('.', '/') + "/rs/controllers");
        for (Map.Entry<String, List<OperationModel>> entry : new TreeMap<>(controllers).entrySet()) {
            String controllerBaseName = sanitizeTypeName(entry.getKey());
            String controllerName = controllerBaseName + "RestController";
            String backendClientBase = sanitizeTypeName(backendClientByController.getOrDefault(entry.getKey(), entry.getKey()));
            String mapperType = mapperBaseName(controllerBaseName) + "Mapper";
            Map<String, String> values = new LinkedHashMap<>();
            values.put("packageName", pkg + ".rs.controllers");
            values.put("className", controllerName);
            values.put("exceptionMapperImport", pkg + ".rs.mappers.ExceptionMapper");
            values.put("apiServiceImportStatement", implementFrontendApi
                    ? "import gen." + pkg + ".rs.internal." + controllerBaseName + "ApiService;"
                    : "");
            values.put("frontendModelImportStatement", implementFrontendApi
                    ? "import gen." + pkg + ".rs.internal.model.*;"
                    : "");
            values.put("backendModelImportStatement", !implementFrontendApi && hasAnyRequestBody(entry.getValue())
                    ? "import gen." + pkg + ".backend.client.model.*;"
                    : "");
            values.put("apiServiceTypeSuffix", implementFrontendApi ? " implements " + controllerBaseName + "ApiService" : "");
            values.put("backendClientImport", "gen." + pkg + ".backend.client.api." + backendClientBase + "Api");
            values.put("backendClientType", backendClientBase + "Api");
            values.put("mapperImport", pkg + ".rs.mappers." + mapperType);
            values.put("mapperType", mapperType);
            values.put("methods", buildControllerMethods(entry.getValue(), implementFrontendApi, todoStubMode));
            writeTemplate(baseDir.resolve(controllerName + ".java"), "entity/Controller.java.tpl", values);
        }
    }
    public void writeMapperClasses(Path projectDir, String pkg, List<SchemaModel> frontend, List<SchemaModel> backend) throws IOException {
        Path baseDir = projectDir.resolve("src/main/java/" + pkg.replace('.', '/') + "/rs/mappers");
        Map<String, SchemaModel> backendByNormalized = new TreeMap<>();
        for (SchemaModel schema : backend) {
            backendByNormalized.put(normalizeEntityName(schema.name()), schema);
        }
        Map<String, String> normalizedToMapper = new LinkedHashMap<>();
        for (SchemaModel source : frontend) {
            String normalized = normalizeEntityName(source.name());
            SchemaModel target = backendByNormalized.get(normalized);
            if (target == null || !shouldGenerateMapper(source.name())) {
                continue;
            }
            normalizedToMapper.put(normalized, mapperBaseName(sanitizeTypeName(source.name())) + "Mapper");
        }
        writeTemplate(baseDir.resolve("ExceptionMapper.java"), "entity/ExceptionMapper.java.tpl",
                Map.of("packageName", pkg + ".rs.mappers"));
        for (SchemaModel source : frontend) {
            String normalized = normalizeEntityName(source.name());
            SchemaModel target = backendByNormalized.get(normalized);
            if (target == null || !shouldGenerateMapper(source.name())) {
                continue;
            }
            String sourceType = sanitizeTypeName(source.name());
            String targetType = sanitizeTypeName(target.name());
            String mapperName = mapperBaseName(sourceType) + "Mapper";
            String sourceModelType = frontendModelTypeForSchema(sourceType);
            String targetModelType = targetType;
            String sourceImport = "gen." + pkg + ".rs.internal.model." + sourceModelType;
            String targetImport = "gen." + pkg + ".backend.client.model." + targetModelType;
            boolean sameSimpleType = sourceModelType.equals(targetModelType);
            Map<String, String> values = new LinkedHashMap<>();
            values.put("packageName", pkg + ".rs.mappers");
            values.put("sourceImportStatement", sameSimpleType ? "" : "import " + sourceImport + ";");
            values.put("targetImportStatement", sameSimpleType ? "" : "import " + targetImport + ";");
            values.put("className", mapperName);
            values.put("sourceTypeRef", sameSimpleType ? sourceImport : sourceModelType);
            values.put("targetTypeRef", sameSimpleType ? targetImport : targetModelType);
            values.put("usesClause", buildMapperUsesClause(source, normalizedToMapper));
            writeTemplate(baseDir.resolve(mapperName + ".java"), "entity/Mapper.java.tpl", values);
        }
    }
    public void writeTestScaffold(Path projectDir, String pkg, Map<String, List<OperationModel>> controllers,
                                   Map<String, String> backendClientByController) throws IOException {
        Path baseDir = projectDir.resolve("src/test/java/" + pkg.replace('.', '/') + "/rs");
        writeTemplate(baseDir.resolve("AbstractTest.java"), "test/AbstractTest.java.tpl",
                Map.of("packageName", pkg + ".rs"));
        for (String key : new TreeMap<>(controllers).keySet()) {
            String controllerBaseName = sanitizeTypeName(key);
            String controllerName = controllerBaseName + "RestController";
            Map<String, String> values = new LinkedHashMap<>();
            values.put("packageName", pkg + ".rs");
            values.put("controllerImport", pkg + ".rs.controllers." + controllerName);
            values.put("className", controllerName);
            values.put("endpointTests", buildEndpointTests(controllers.getOrDefault(key, List.of())));
            writeTemplate(baseDir.resolve(controllerName + "Test.java"), "test/ControllerTest.java.tpl", values);
            writeTemplate(baseDir.resolve(controllerName + "IT.java"), "test/ControllerIT.java.tpl", values);
        }
        Path resourcesDir = projectDir.resolve("src/test/resources");
        writeTemplate(resourcesDir.resolve("mockserver.properties"), "test/mockserver.properties.tpl", Map.of());
        writeTemplate(resourcesDir.resolve("mockserver/mandatory_tokens.json"), "test/mockserver-mandatory-tokens.json.tpl", Map.of());
        writeTemplate(resourcesDir.resolve("mockserver/permissions.json"), "test/mockserver-permissions.json.tpl", Map.of());
    }
    private String buildControllerMethods(List<OperationModel> operations, boolean implementFrontendApi, boolean todoStubMode) {
        StringBuilder sb = new StringBuilder();
        for (OperationModel op : operations) {
            List<String> params = pathParams(op.path());
            String signature = methodSignature(params);

            // if request body, add dto param
            String bodyParam = null;
            String dtoType = null;
            if (op.hasRequestBody()) {
                dtoType = implementFrontendApi
                        ? frontendModelTypeForSchema(op.requestBodyType())
                        : backendModelTypeForSchema(op.requestBodyType());
                bodyParam = buildRequestBodyParamName(op.requestBodyType());
                String bodyParamDecl = dtoType + " " + bodyParam;
                signature = signature.isBlank() ? bodyParamDecl : signature + ", " + bodyParamDecl;
            }

            sb.append("    @").append(op.httpMethod()).append("\n")
                    .append("    @Path(\"").append(op.path()).append("\")\n")
                    .append(implementFrontendApi ? "    @Override\n" : "")
                    .append("    public Response ").append(sanitizeMethodName(op.operationId())).append("(")
                    .append(signature).append(") {\n");

            if (todoStubMode) {
                sb.append("        // TODO implement backend call and mapper conversion.\n")
                        .append("        return null;\n");
            } else {
                // Build backend client call args
                List<String> callArgs = new ArrayList<>(params.stream().map(this::sanitizeFieldName).toList());
                if (bodyParam != null) {
                    if (implementFrontendApi) {
                        callArgs.add("mapper.toBackend(" + bodyParam + ")");
                    } else {
                        callArgs.add(bodyParam);
                    }
                }
                String clientCall = "client." + sanitizeMethodName(op.operationId()) + "(" + String.join(", ", callArgs) + ")";

                String responseType = op.responseType();
                if (responseType == null || responseType.isBlank()) {
                    // no response body — DELETE / fire-and-forget style
                    sb.append("        ").append(clientCall).append(";\n")
                            .append("        return Response.noContent().build();\n");
                } else if (implementFrontendApi) {
                    if (responseType.startsWith("List<")) {
                        sb.append("        return Response.ok(mapper.toFrontendList(").append(clientCall).append(")).build();\n");
                    } else {
                        sb.append("        return Response.ok(mapper.toFrontend(").append(clientCall).append(")).build();\n");
                    }
                } else {
                    // fallback: backend client returns Response, propagate directly
                    int successCode = op.successStatusCode() > 0 ? op.successStatusCode() : 200;
                    String entityType = backendModelTypeForSchema(responseType);
                    sb.append("        try (Response backendResponse = ").append(clientCall).append(") {\n")
                      .append("            ").append(entityType).append(" result = backendResponse.readEntity(").append(entityType).append(".class);\n")
                      .append("            return Response.status(").append(successCode).append(").entity(result).build();\n")
                      .append("        }\n");
                }
            }
            sb.append("    }\n\n");
        }
        return sb.toString();
    }

    private String buildEndpointTests(List<OperationModel> operations) {
        StringBuilder sb = new StringBuilder();
        for (OperationModel op : operations) {
            String methodName = sanitizeMethodName(op.operationId());
            String testName = methodName + "Test";
            String httpMethod = op.httpMethod();
            String opPath = op.path();
            boolean hasBody = op.hasRequestBody();
            boolean hasResponse = op.hasResponseBody();
            List<String> pathParamNames = pathParams(opPath);
            int successStatus = resolveSuccessStatus(op.successStatusCode(), httpMethod, hasResponse);
            // Build RestAssured path: replace {param} with "test-id" literals
            String restAssuredPath = opPath;
            for (String p : pathParamNames) {
                restAssuredPath = restAssuredPath.replace("{" + p + "}", "test-id");
            }
            // Mock server path (same)
            String mockServerPath = restAssuredPath;
            sb.append("    @Test\n")
              .append("    void ").append(testName).append("() {\n");
            // unauthorized
            sb.append("        // unauthorized\n")
              .append("        given()\n")
              .append("                .when()\n");
            if (hasBody) {
                sb.append("                .contentType(APPLICATION_JSON)\n")
                  .append("                .body(\"{}\")\n");
            }
            sb.append("                .").append(httpMethod.toLowerCase(Locale.ROOT)).append("(\"").append(restAssuredPath).append("\")\n")
              .append("                .then()\n")
              .append("                .statusCode(Response.Status.UNAUTHORIZED.getStatusCode());\n\n");
            // mock backend + authorized
            sb.append("        // mock backend\n")
              .append("        mockServerClient.when(\n")
              .append("                HttpRequest.request()\n")
              .append("                        .withPath(\"").append(mockServerPath).append("\")\n")
              .append("                        .withMethod(\"").append(httpMethod).append("\"))\n")
              .append("                .withId(MOCK_ID).respond(\n")
              .append("                        HttpResponse.response()\n")
              .append("                                .withStatusCode(").append(successStatus).append(")");
            if (hasResponse) {
                sb.append("\n                                .withHeader(\"Content-Type\", \"application/json\")\n")
                  .append("                                .withBody(\"{}\")");
            }
            sb.append(");\n\n");
            sb.append("        given()\n")
              .append("                .when()\n")
              .append("                .auth().oauth2(keycloakClient.getAccessToken(ADMIN))\n")
              .append("                .header(APM_HEADER_PARAM, ADMIN)\n");
            if (hasBody) {
                sb.append("                .contentType(APPLICATION_JSON)\n")
                  .append("                .body(\"{}\")\n");
            }
            sb.append("                .").append(httpMethod.toLowerCase(Locale.ROOT)).append("(\"").append(restAssuredPath).append("\")\n")
              .append("                .then()\n")
              .append("                .statusCode(").append(successStatus).append(");\n");
            sb.append("    }\n\n");
        }
        return sb.toString();
    }
    private int resolveSuccessStatus(int openApiStatusCode, String httpMethod, boolean hasResponse) {
        if (openApiStatusCode > 0) {
            return openApiStatusCode;
        }
        if ("DELETE".equals(httpMethod) || !hasResponse) {
            return 204;
        }
        if ("POST".equals(httpMethod)) {
            return 201;
        }
        return 200;
    }
    private String buildMockitoAnyArgs(int count) {
        if (count == 0) {
            return "";
        }
        return String.join(", ", java.util.Collections.nCopies(count, "org.mockito.ArgumentMatchers.any()"));
    }
    private List<String> pathParams(String path) {
        Matcher matcher = PATH_PARAM_PATTERN.matcher(path);
        List<String> result = new ArrayList<>();
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }
    private String methodSignature(List<String> params) {
        return String.join(", ", params.stream()
                .map(name -> "@PathParam(\"" + name + "\") String " + sanitizeFieldName(name))
                .toList());
    }
    private void writeTemplate(Path path, String templateName, Map<String, String> values) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, templateRenderer.render(templateName, values));
    }
    private String javaVersion(DependencyProfile profile) {
        return profile == DependencyProfile.MODERN_3_1_PLUS ? "25" : "17";
    }
    private boolean usesLegacyJunitArtifacts(DependencyProfile profile) {
        return profile == DependencyProfile.LEGACY_UP_TO_2_5;
    }
    private String sanitizeTypeName(String value) {
        String clean = value.replaceAll("[^a-zA-Z0-9]", " ");
        String[] parts = clean.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.isEmpty() ? "GeneratedType" : sb.toString();
    }
    private String sanitizeFieldName(String value) {
        String cleaned = value.replaceAll("[^a-zA-Z0-9]", " ").trim();
        if (cleaned.isBlank()) {
            return "field";
        }
        // If already a single camelCase/identifier token (no spaces produced), preserve as-is
        // just ensure first char is lowercase
        if (!cleaned.contains(" ")) {
            String result = Character.toLowerCase(cleaned.charAt(0)) + cleaned.substring(1);
            if (Character.isDigit(result.charAt(0))) {
                result = "field" + result;
            }
            return result;
        }
        String[] parts = cleaned.split("\\s+");
        StringBuilder sb = new StringBuilder(parts[0].toLowerCase(Locale.ROOT));
        for (int i = 1; i < parts.length; i++) {
            sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        if (Character.isDigit(sb.charAt(0))) {
            sb.insert(0, "field");
        }
        return sb.toString();
    }
    private String sanitizeMethodName(String value) {
        String name = sanitizeFieldName(value);
        if (Character.isDigit(name.charAt(0))) {
            return "op" + name;
        }
        return name;
    }

    private String frontendModelTypeForSchema(String schemaType) {
        if (schemaType == null || schemaType.isBlank()) {
            return schemaType;
        }
        String normalized = schemaType.trim();
        if (normalized.startsWith("List<") && normalized.endsWith(">")) {
            String inner = normalized.substring(5, normalized.length() - 1).trim();
            return "List<" + frontendModelTypeForSchema(inner) + ">";
        }
        if ("Object".equals(normalized)) {
            return normalized;
        }
        String sanitized = sanitizeTypeName(normalized);
        // Avoid double DTO suffix (schema may already end with DTO)
        if (sanitized.endsWith("DTO")) {
            return sanitized;
        }
        return sanitized + "DTO";
    }

    private String backendModelTypeForSchema(String schemaType) {
        if (schemaType == null || schemaType.isBlank()) {
            return schemaType;
        }
        String normalized = schemaType.trim();
        if (normalized.startsWith("List<") && normalized.endsWith(">")) {
            String inner = normalized.substring(5, normalized.length() - 1).trim();
            return "List<" + backendModelTypeForSchema(inner) + ">";
        }
        if ("Object".equals(normalized)) {
            return normalized;
        }
        return sanitizeTypeName(normalized);
    }

    private boolean hasAnyRequestBody(List<OperationModel> operations) {
        return operations.stream().anyMatch(OperationModel::hasRequestBody);
    }

    private String buildRequestBodyParamName(String schemaType) {
        if (schemaType == null || schemaType.isBlank()) {
            return "requestDto";
        }
        String normalized = schemaType.trim();
        if (normalized.startsWith("List<") && normalized.endsWith(">")) {
            normalized = normalized.substring(5, normalized.length() - 1).trim();
        }
        String baseName = sanitizeTypeName(normalized).replaceAll("(DTO)+$", "");
        if (baseName.isBlank()) {
            return "requestDto";
        }
        return sanitizeFieldName(baseName) + "Dto";
    }
    private String normalizeEntityName(String value) {
        return sanitizeTypeName(value)
                .replaceAll("(Dto|DTO|Response|Request|Internal|External)$", "")
                .toLowerCase(Locale.ROOT);
    }
    private String mapperBaseName(String sanitizedTypeName) {
        String stripped = sanitizedTypeName.replaceAll("(Dto|DTO|Response|Request|Internal|External)$", "");
        return stripped.isBlank() ? sanitizedTypeName : stripped;
    }
    private boolean shouldGenerateMapper(String schemaName) {
        String sanitized = sanitizeTypeName(schemaName);
        return !(sanitized.startsWith("ProblemDetail")
                || sanitized.equals("OffsetDateTime")
                || sanitized.endsWith("SearchCriteria")
                || sanitized.endsWith("PageResult"));
    }
    private String buildMapperUsesClause(SchemaModel source, Map<String, String> normalizedToMapper) {
        List<String> uses = new ArrayList<>();
        String current = mapperBaseName(sanitizeTypeName(source.name())) + "Mapper";
        for (String type : source.fields().values()) {
            String mapper = normalizedToMapper.get(normalizeEntityName(type));
            if (mapper != null && !mapper.equals(current) && !uses.contains(mapper)) {
                uses.add(mapper);
            }
        }
        if (uses.isEmpty()) {
            return "";
        }
        return ", uses = { " + String.join(", ", uses.stream().map(use -> use + ".class").toList()) + " }";
    }
    private String toPropertyToken(String value) {
        String normalized = value.replaceAll("[^a-zA-Z0-9]", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
        return normalized.toLowerCase(Locale.ROOT);
    }
    private String defaultPermissionKey(String artifactId) {
        String[] tokens = artifactId.split("[-.]");
        for (String token : tokens) {
            String lower = token.toLowerCase(Locale.ROOT);
            if (!lower.isBlank() && !"onecx".equals(lower) && !"bff".equals(lower)) {
                return lower;
            }
        }
        return "resource";
    }
}





























