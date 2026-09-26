package com.metanet4j.sdk.utxo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metanet4j.base.util.JacksonUtil;
import com.metanet4j.sdk.utxo.impl.bitails.BitailsClient;
import com.metanet4j.sdk.utxo.impl.bitails.BitailsUtxoProvider;
import com.metanet4j.sdk.utxo.impl.gorillapool.GorillaClient;
import com.metanet4j.sdk.utxo.impl.gorillapool.OrdinalsOriginGorillaUtxoProvider;
import com.metanet4j.sdk.address.AddressEnhance;
import io.bitcoinsv.bitcoinjsv.core.AddressLite;
import io.bitcoinsv.bitcoinjsv.params.MainNetParams;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
class BitailsAndGorillaHttpIntegrationTest {

    @Test
    void bitailsUsesLocalEndpointForReadAndBroadcast() throws IOException {
        List<String> requests = new CopyOnWriteArrayList<>();
        AtomicReference<String> postedRaw = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            String path = exchange.getRequestURI().getPath();
            String body;
            if (path.endsWith("/unspent")) {
                body = "{\"address\":\"demo\",\"unspent\":[{\"txid\":\"a\",\"vout\":2,\"satoshis\":42}]}";
            } else if (path.endsWith("/balance")) {
                body = "{\"address\":\"demo\",\"confirmed\":42,\"unconfirmed\":0,\"summary\":42,\"count\":1}";
            } else if (path.endsWith("/broadcast")) {
                Map<?, ?> posted = JacksonUtil.string2Obj(
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), Map.class);
                postedRaw.set((String) posted.get("raw"));
                body = "{\"txid\":\"local-only\"}";
            } else {
                throw new AssertionError(path);
            }
            respond(exchange, body);
        });
        try {
            String endpoint = endpoint(server);
            assertEquals("demo", BitailsClient.getAddressUnspentResDto(endpoint, "demo", 1, 2).getAddress());
            assertEquals(42, BitailsClient.getAddressBalanceResDto(endpoint, "demo").getConfirmed());
            assertEquals("{\"txid\":\"local-only\"}", BitailsClient.broadcast(endpoint, "0102"));
            assertEquals("0102", postedRaw.get());
            assertEquals(List.of("GET /address/demo/unspent?from=1&limit=2", "GET /address/demo/balance",
                    "POST /tx/broadcast"), requests);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void gorillaUsesLocalEndpointForOriginLookup() throws IOException {
        List<String> requests = new CopyOnWriteArrayList<>();
        HttpServer server = server(exchange -> {
            requests.add(exchange.getRequestURI().toString());
            respond(exchange, "{\"txid\":\"tx-local\",\"vout\":1,\"satoshis\":7,\"script\":\"AA==\"}");
        });
        try {
            assertEquals("tx-local", GorillaClient.GetTxoByOrigin(endpoint(server), "origin-1").getTxid());
            assertEquals(List.of("/api/txos/origin-1?script=true"), requests);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void malformedHttpPayloadReportsTheLocalRequest() throws IOException {
        HttpServer server = server(exchange -> respond(exchange, "{invalid"));
        try {
            RuntimeException error = assertThrows(RuntimeException.class,
                    () -> BitailsClient.getAddressBalanceResDto(endpoint(server), "bad"));
            assertTrue(error.getMessage().contains("/address/bad/balance"));
            RuntimeException gorillaError = assertThrows(RuntimeException.class,
                    () -> GorillaClient.GetTxoByOrigin(endpoint(server), "bad-origin"));
            assertTrue(gorillaError.getMessage().contains("/api/txos/bad-origin"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void nonSuccessStatusIsNotParsedAsSuccessfulBalance() throws IOException {
        HttpServer server = server(exchange -> respond(exchange, 503, "{\"address\":\"wrong\"}"));
        try {
            RuntimeException error = assertThrows(RuntimeException.class,
                    () -> BitailsClient.getAddressBalanceResDto(endpoint(server), "bad"));
            assertTrue(error.getMessage().contains("503"));
            assertTrue(error.getMessage().contains("/address/bad/balance"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void providersMapLocalHttpResponsesAndPropagateServerFailures() throws IOException {
        AddressLite address = AddressEnhance.fromBase58(MainNetParams.get(),
                "1WffojxvgpQBmUTigoss7VUdfN45JiiRK");
        List<String> requests = new CopyOnWriteArrayList<>();
        AtomicBoolean failBitails = new AtomicBoolean();
        HttpServer server = server(exchange -> {
            requests.add(exchange.getRequestURI().toString());
            String path = exchange.getRequestURI().getPath();
            if (path.contains("failure") || (failBitails.get() && path.endsWith("/unspent"))) {
                respond(exchange, 503, "{\"error\":\"unavailable\"}");
            } else if (path.endsWith("/unspent")) {
                respond(exchange, "{\"address\":\"" + address.toBase58() + "\",\"unspent\":[{\"txid\":\""
                        + "1".repeat(64) + "\",\"vout\":2,\"satoshis\":42}]}");
            } else {
                respond(exchange, "{\"txid\":\"" + "2".repeat(64)
                        + "\",\"vout\":3,\"satoshis\":7,\"script\":\"AA==\"}");
            }
        });
        try {
            String endpoint = endpoint(server);
            assertEquals(42, new BitailsUtxoProvider(endpoint).listUxtos(List.of(address))
                    .getFirst().getValue().getValue());
            assertEquals(7, new OrdinalsOriginGorillaUtxoProvider(endpoint)
                    .listUxtos(List.of("origin-local")).getFirst().getValue().getValue());
            assertEquals(List.of("/address/" + address.toBase58() + "/unspent?from=0&limit=100",
                    "/api/txos/origin-local?script=true"), requests);
            failBitails.set(true);
            assertTrue(assertThrows(RuntimeException.class,
                    () -> new BitailsUtxoProvider(endpoint).listUxtos(List.of(address)))
                    .getMessage().contains("503"));
            assertTrue(assertThrows(RuntimeException.class,
                    () -> new OrdinalsOriginGorillaUtxoProvider(endpoint)
                            .listUxtos(List.of("failure"))).getMessage().contains("503"));
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer server(Handler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    private static String endpoint(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        respond(exchange, 200, body);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
