package com.metanet4j.sdk.sigma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.params.Net;
import io.bitcoinsv.bitcoinjsv.script.Script;
import io.bitcoinsv.bitcoinjsv.script.ScriptBuilder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SigmaProtocolModelTest {

    @Test
    void signatureAndResponseRetainProtocolFields() {
        Sig sig = new Sig("address", "base64-signature", Algorithm.BSM.name(), 2, 3);
        assertEquals("address", sig.getAddress());
        assertEquals("base64-signature", sig.getSignature());
        assertEquals("BSM", sig.getAlgorithm());
        assertEquals(2, sig.getVin());
        assertEquals(3, sig.getTargetVout());

        Sig mutable = new Sig();
        mutable.setAddress("other");
        mutable.setSignature("signature");
        assertEquals("other", mutable.getAddress());
        assertEquals("signature", mutable.getSignature());

        Script script = new ScriptBuilder().data("SIGMA".getBytes(StandardCharsets.US_ASCII)).build();
        Transaction transaction = new Transaction(Net.MAINNET);
        SignResponse response = new SignResponse("address", "base64-signature", "BSM", 2, 3,
                script, transaction);
        assertEquals(sig.getAddress(), response.getAddress());
        assertEquals(sig.getSignature(), response.getSignature());
        assertSame(script, response.getSigmaScript());
        assertSame(transaction, response.getSignedTx());
        assertSame(Algorithm.BSM, Algorithm.valueOf("BSM"));
    }
}
