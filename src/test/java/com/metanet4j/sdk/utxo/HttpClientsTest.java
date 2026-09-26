package com.metanet4j.sdk.utxo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import com.metanet4j.base.util.JacksonUtil;
import com.metanet4j.sdk.utxo.impl.HttpClientFactory;
import com.metanet4j.sdk.utxo.impl.bitails.BitailsClient;
import com.metanet4j.sdk.utxo.impl.bitails.dto.req.TransactionBroadcastReqDto;
import com.metanet4j.sdk.utxo.impl.bitails.dto.res.AddressBalanceResDto;
import com.metanet4j.sdk.utxo.impl.bitails.dto.res.AddressUnspentResDto;
import com.metanet4j.sdk.utxo.impl.gorillapool.GorillaClient;
import com.metanet4j.sdk.utxo.impl.gorillapool.dto.res.Txo;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class HttpClientsTest {

    private static final HttpRequest PLACEHOLDER = HttpRequest.newBuilder(URI.create("http://127.0.0.1/")).build();

    @Test
    void bitailsQueriesUseDefaultAndExplicitEndpoints() {
        List<String> urls = new ArrayList<>();
        AddressUnspentResDto unspent = new AddressUnspentResDto();
        AddressBalanceResDto balance = new AddressBalanceResDto();
        try (MockedStatic<HttpClientFactory> transport = mockStatic(HttpClientFactory.class)) {
            transport.when(() -> HttpClientFactory.createGetHttpRequest(anyString(), anyLong()))
                    .thenAnswer(call -> {
                        urls.add(call.getArgument(0));
                        return PLACEHOLDER;
                    });
            transport.when(() -> HttpClientFactory.send(PLACEHOLDER, AddressUnspentResDto.class))
                    .thenReturn(unspent);
            transport.when(() -> HttpClientFactory.send(PLACEHOLDER, AddressBalanceResDto.class))
                    .thenReturn(balance);

            assertSame(unspent, BitailsClient.getAddressUnspentResDto("demo", 1, 2));
            assertSame(unspent, BitailsClient.getAddressUnspentResDto("http://local", "demo", 3, 4));
            assertSame(balance, BitailsClient.getAddressBalanceResDto("demo"));
            assertSame(balance, BitailsClient.getAddressBalanceResDto("http://local", "demo"));
        }
        assertEquals(List.of("https://api.bitails.io/address/demo/unspent?from=1&limit=2",
                "http://local/address/demo/unspent?from=3&limit=4",
                "https://api.bitails.io/address/demo/balance", "http://local/address/demo/balance"), urls);
    }

    @Test
    void broadcastSerializesRawTransactionForBothEndpoints() {
        List<String> urls = new ArrayList<>();
        List<String> bodies = new ArrayList<>();
        try (MockedStatic<HttpClientFactory> transport = mockStatic(HttpClientFactory.class)) {
            transport.when(() -> HttpClientFactory.createPostHttpRequest(anyString(), anyLong(), anyString()))
                    .thenAnswer(call -> {
                        urls.add(call.getArgument(0));
                        bodies.add(call.getArgument(2));
                        return PLACEHOLDER;
                    });
            transport.when(() -> HttpClientFactory.send(PLACEHOLDER, String.class))
                    .thenReturn("accepted");

            TransactionBroadcastReqDto request = new TransactionBroadcastReqDto();
            request.setRaw("0304");
            assertEquals("accepted", BitailsClient.broadcast("0102"));
            assertEquals("accepted", BitailsClient.broadcast("http://local", "0102"));
            assertEquals("accepted", BitailsClient.broadcast(request));
            assertEquals("accepted", BitailsClient.broadcast("http://local", request));
        }
        assertEquals(List.of("https://api.bitails.io/tx/broadcast", "http://local/tx/broadcast",
                "https://api.bitails.io/tx/broadcast", "http://local/tx/broadcast"), urls);
        assertEquals(List.of("0102", "0102", "0304", "0304"), bodies.stream()
                .map(body -> (String) JacksonUtil.string2Obj(body, Map.class).get("raw")).toList());
    }

    @Test
    void gorillaQueriesUseDefaultAndExplicitEndpoints() {
        List<String> urls = new ArrayList<>();
        Txo txo = new Txo();
        try (MockedStatic<HttpClientFactory> transport = mockStatic(HttpClientFactory.class)) {
            transport.when(() -> HttpClientFactory.createGetHttpRequest(anyString(), anyLong()))
                    .thenAnswer(call -> {
                        urls.add(call.getArgument(0));
                        return PLACEHOLDER;
                    });
            transport.when(() -> HttpClientFactory.send(PLACEHOLDER, Txo.class)).thenReturn(txo);

            assertSame(txo, GorillaClient.GetTxoByOrigin("origin"));
            assertSame(txo, GorillaClient.GetTxoByOrigin("http://local", "origin"));
        }
        assertEquals(List.of("https://ordinals.gorillapool.io/api/txos/origin?script=true",
                "http://local/api/txos/origin?script=true"), urls);
    }
}
