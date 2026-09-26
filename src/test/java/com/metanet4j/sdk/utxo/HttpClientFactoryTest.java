package com.metanet4j.sdk.utxo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.metanet4j.sdk.utxo.impl.HttpClientFactory;
import com.metanet4j.sdk.utxo.impl.bitails.dto.res.AddressBalanceResDto;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class HttpClientFactoryTest {

    @Test
    void createsRequestsWithMethodHeadersAndTimeout() {
        HttpRequest get = HttpClientFactory.createGetHttpRequest("http://127.0.0.1/read", 1200);
        HttpRequest post = HttpClientFactory.createPostHttpRequest("http://127.0.0.1/write", 2400, "{}");

        assertEquals("GET", get.method());
        assertEquals(Duration.ofMillis(1200), get.timeout().orElseThrow());
        assertEquals("POST", post.method());
        assertEquals("application/json", post.headers().firstValue("Content-Type").orElseThrow());
        assertEquals(Duration.ofMillis(2400), post.timeout().orElseThrow());
        assertSame(HttpClientFactory.createHttpClient(), HttpClientFactory.createHttpClient());
        assertThrows(RuntimeException.class, () -> HttpClientFactory.createGetHttpRequest(":bad", 100));
        assertThrows(RuntimeException.class, () -> HttpClientFactory.createPostHttpRequest(":bad", 100, "{}"));
    }

    @Test
    void decodesBodyAndReportsMalformedJson() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpRequest request = HttpClientFactory.createGetHttpRequest("http://127.0.0.1/balance", 1000);
        HttpResponse<String> response = mockResponse("{\"confirmed\":42}");
        doReturn(response).when(client).send(eq(request), any());

        assertEquals(42, HttpClientFactory.send(client, request, AddressBalanceResDto.class).getConfirmed());
        when(response.body()).thenReturn("{oops");
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> HttpClientFactory.send(client, request, AddressBalanceResDto.class));
        assertTrue(error.getMessage().contains("/balance"));
        when(response.body()).thenReturn("");
        assertNull(HttpClientFactory.send(client, request, AddressBalanceResDto.class));
        when(response.statusCode()).thenReturn(503);
        when(response.body()).thenReturn("{\"confirmed\":42}");
        RuntimeException serviceError = assertThrows(RuntimeException.class,
                () -> HttpClientFactory.send(client, request, AddressBalanceResDto.class));
        assertTrue(serviceError.getMessage().contains("503"));
        doReturn(null).when(client).send(eq(request), any());
        assertNull(HttpClientFactory.send(client, request, AddressBalanceResDto.class));
    }

    @Test
    void returnsNullForIoFailureAndRestoresInterruptedStatus() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpRequest request = HttpClientFactory.createGetHttpRequest("http://127.0.0.1/failure", 1000);
        doThrow(new IOException("offline")).when(client).send(eq(request), any());
        assertNull(HttpClientFactory.send(client, request, String.class));

        doThrow(new InterruptedException("cancelled")).when(client).send(eq(request), any());
        assertNull(HttpClientFactory.send(client, request, String.class));
        assertTrue(Thread.interrupted());
    }

    private static HttpResponse<String> mockResponse(String body) {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        return response;
    }
}
