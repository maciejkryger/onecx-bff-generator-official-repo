package ${packageName};
import io.quarkiverse.mockserver.test.InjectMockServerClient;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.tkit.quarkus.log.cdi.LogService;
import ${controllerImport};
import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
@QuarkusTest
@LogService
class ${className}Test extends AbstractTest {
    @InjectMockServerClient
    MockServerClient mockServerClient;
    @BeforeEach
    void resetExpectation() {
        try {
            mockServerClient.clear(MOCK_ID);
        } catch (Exception ex) {
            // mockId not existing
        }
    }
${endpointTests}
}
