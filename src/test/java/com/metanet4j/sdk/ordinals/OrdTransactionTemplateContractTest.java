package com.metanet4j.sdk.ordinals;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.hutool.core.codec.Base64;
import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.bap.BapBase;
import com.metanet4j.sdk.exception.SignErrorException;
import com.metanet4j.sdk.utxo.UTXOProvider;
import io.bitcoinsv.bitcoinjsv.core.Address;
import io.bitcoinsv.bitcoinjsv.core.AddressLite;
import io.bitcoinsv.bitcoinjsv.core.Coin;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import io.bitcoinsv.bitcoinjsv.core.UTXO;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.signers.TransactionSigner;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrdTransactionTemplateContractTest {

    private final BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
    private final Address payment = new Address(bap.getPayAccountAddress());
    private final Address destination = bap.getOrdAddress();
    private final BapBase.BapProviderKeyBag keyBag = new BapBase.BapProviderKeyBag(bap);
    private final UTXOProvider<AddressLite, UTXO> provider = addresses -> {
        assertEquals(List.of(payment.toBase58()),
                addresses.stream().map(AddressLite::toBase58).toList());
        return List.of(paymentUtxo(1));
    };

    @Test
    void postOrdinalIncludesTheAppAndPostMetadata() {
        OrdTransactionTemplate template = new OrdTransactionTemplate();
        OrdScriptBuilder.Inscription inscription = inscription("post", "text/plain");
        TransactionSigner signer = mock(TransactionSigner.class);
        when(signer.signInputs(any(), any())).thenReturn(true);
        OrdScriptBuilder.ORDMap metadata = new OrdScriptBuilder.ORDMap();
        metadata.put("app", bap.getAppName());
        metadata.put("type", "post");

        Transaction transaction = template.createPostOrdinal(
                List.of(bap.getPayAccountAddress()), provider, destination, payment,
                500, inscription, bap, keyBag, signer);

        byte[] inscriptionScript = OrdScriptBuilder.buildInscription(destination,
                "post".getBytes(StandardCharsets.US_ASCII), "text/plain", metadata).getProgram();
        assertArrayEquals(inscriptionScript,
                Arrays.copyOf(transaction.getOutput(0).getScriptBytes(), inscriptionScript.length));
    }

    @Test
    void reinscriptionKeepsItsMediaType() {
        OrdTransactionTemplate template = new OrdTransactionTemplate();
        OrdScriptBuilder.Inscription inscription = inscription("new", "text/plain");
        OrdScriptBuilder.ORDMap metadata = new OrdScriptBuilder.ORDMap();
        metadata.put("type", "post");
        TransactionSigner signer = mock(TransactionSigner.class);
        when(signer.signInputs(any(), any())).thenReturn(true);

        Transaction transaction = template.sendOrdinal(bap, ordinalUtxo(2),
                paymentUtxo(3), destination, payment, 500, inscription, metadata,
                keyBag, signer);

        assertArrayEquals(OrdScriptBuilder.buildInscription(destination,
                "new".getBytes(StandardCharsets.US_ASCII), "text/plain", metadata).getProgram(),
                transaction.getOutput(0).getScriptBytes());
    }

    @Test
    void sendOrdinalPropagatesSignerFailure() {
        OrdTransactionTemplate template = new OrdTransactionTemplate();
        TransactionSigner signer = mock(TransactionSigner.class);

        assertThrows(SignErrorException.class, () -> template.sendOrdinal(bap,
                ordinalUtxo(4), paymentUtxo(5), destination, payment, 500,
                null, null, keyBag, signer));
    }

    private OrdScriptBuilder.Inscription inscription(String value, String contentType) {
        return OrdScriptBuilder.Inscription.builder().contentType(contentType)
                .dataB64(Base64.encode(value.getBytes(StandardCharsets.US_ASCII))).build();
    }

    private UTXO paymentUtxo(int firstHashByte) {
        return new UTXO(hash(firstHashByte), 0, Coin.valueOf(20_000), 0,
                false, null, payment.toBase58());
    }

    private UTXO ordinalUtxo(int firstHashByte) {
        return new UTXO(hash(firstHashByte), 0, Coin.SATOSHI, 0, false,
                OrdScriptBuilder.buildInscription(destination,
                        "old".getBytes(StandardCharsets.US_ASCII), "text/plain", null));
    }

    private Sha256Hash hash(int firstByte) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) firstByte;
        return Sha256Hash.wrap(bytes);
    }
}
