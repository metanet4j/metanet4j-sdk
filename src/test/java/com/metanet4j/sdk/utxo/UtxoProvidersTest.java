package com.metanet4j.sdk.utxo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.utxo.impl.bitails.BitailsClient;
import com.metanet4j.sdk.utxo.impl.bitails.BitailsUtxoProvider;
import com.metanet4j.sdk.utxo.impl.bitails.dto.res.AddressUnspentResDto;
import com.metanet4j.sdk.utxo.impl.gorillapool.GorillaClient;
import com.metanet4j.sdk.utxo.impl.gorillapool.OrdinalsOriginGorillaUtxoProvider;
import com.metanet4j.sdk.utxo.impl.gorillapool.dto.res.Txo;
import com.metanet4j.base.bsv.BsvAmount;
import com.metanet4j.base.bsv.BsvUtxo;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class UtxoProvidersTest {

    @Test
    void bitailsMapsUnspentOutputsAndHandlesEmptyResults() {
        AddressEnhance address = AddressEnhance.fromBase58("1WffojxvgpQBmUTigoss7VUdfN45JiiRK");
        AddressUnspentResDto.Unspent output = new AddressUnspentResDto.Unspent();
        output.setTxid("1".repeat(64));
        output.setVout(2);
        output.setSatoshis(42);
        AddressUnspentResDto response = new AddressUnspentResDto();
        response.setAddress(address.toBase58());
        response.setUnspent(List.of(output));

        BitailsUtxoProvider provider = new BitailsUtxoProvider();
        assertTrue(provider.listUxtos(List.of()).isEmpty());
        try (MockedStatic<BitailsClient> client = mockStatic(BitailsClient.class)) {
            client.when(() -> BitailsClient.getAddressUnspentResDto(BitailsClient.BASE_URL, address.toBase58(), 0, 100))
                    .thenReturn(response);
            List<BsvUtxo> mapped = provider.listUxtos(List.of(address));
            assertEquals(1, mapped.size());
            assertEquals("1".repeat(64), mapped.getFirst().getHash().toString());
            assertEquals(2, mapped.getFirst().getIndex());
            assertEquals(BsvAmount.valueOf(42), mapped.getFirst().getValue());
            assertEquals(address.toBase58(), mapped.getFirst().getAddress());

            response.setUnspent(null);
            assertTrue(provider.listUxtos(List.of(address)).isEmpty());
        }
    }

    @Test
    void gorillaChoosesSpendHashWhenAvailable() {
        Txo txo = new Txo();
        txo.setTxid("2".repeat(64));
        txo.setVout(3);
        txo.setSatoshis(51);
        txo.setScript("AA==");
        OrdinalsOriginGorillaUtxoProvider provider = new OrdinalsOriginGorillaUtxoProvider();
        try (MockedStatic<GorillaClient> client = mockStatic(GorillaClient.class)) {
            client.when(() -> GorillaClient.GetTxoByOrigin(GorillaClient.BASE_URL, "origin")).thenReturn(txo);
            BsvUtxo unspent = provider.listUxtos(List.of("origin")).getFirst();
            assertEquals("2".repeat(64), unspent.getHash().toString());
            assertEquals(3, unspent.getIndex());
            assertEquals(BsvAmount.valueOf(51), unspent.getValue());

            txo.setSpend("3".repeat(64));
            BsvUtxo spent = provider.listUxtos(List.of("origin")).getFirst();
            assertEquals("3".repeat(64), spent.getHash().toString());
        }
    }
}
