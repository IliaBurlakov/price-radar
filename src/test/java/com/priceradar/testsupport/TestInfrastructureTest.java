package com.priceradar.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

class TestInfrastructureTest {

    @Test
    void blocksExternalHostsBeforeAnyConnectionIsAttempted() {
        assertThatThrownBy(
                () -> ProxySelector.getDefault().select(URI.create("https://www.wildberries.ru"))
        ).isInstanceOf(AssertionError.class)
                .hasMessageContaining("must not access external host");
    }

    @Test
    void permitsRequestsToLocalHttpStubs() throws Exception {
        try (LocalHttpStub stub = LocalHttpStub.start()) {
            stub.stub("/product", 200, "{\"status\":\"ok\"}");

            HttpRequest request = HttpRequest.newBuilder(stub.baseUri().resolve("/product")).build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("{\"status\":\"ok\"}");
        }
    }
}
