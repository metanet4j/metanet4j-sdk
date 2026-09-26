package com.metanet4j.sdk.script;

import static io.bitcoinsv.bitcoinjsv.script.ScriptOpCodes.OP_TRUE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.ordinals.OrdScriptBuilder;
import io.bitcoinsv.bitcoinjsv.core.Address;
import io.bitcoinsv.bitcoinjsv.core.ECKeyLite;
import io.bitcoinsv.bitcoinjsv.params.MainNetParams;
import io.bitcoinsv.bitcoinjsv.script.Script;
import io.bitcoinsv.bitcoinjsv.script.ScriptBuilder;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScriptExtendContractTest {

    private static final ECKeyLite KEY = ECKeyLite.fromPrivate(BigInteger.TWO);
    private static final byte[] SIGNATURE = {1, 2, 3};

    @Test
    void estimatesSpendSizeForCommonAndOrdinalScripts() {
        Script address = ScriptBuilder.createOutputScript(AddressEnhance.fromECkeyLite(KEY));
        Script rawKey = ScriptBuilder.createOutputScript(KEY);
        Script multiSig = ScriptBuilder.createMultiSigOutputScript(1, List.of(KEY));
        Script p2sh = ScriptBuilder.createP2SHOutputScript(multiSig);
        Script ordinal = OrdScriptBuilder.buildInscription(new Address(MainNetParams.get(), KEY.getPubKeyHash()),
                new byte[] {1}, "text/plain", null);

        assertEquals(1234L, new ScriptExtend(address.getProgram(), 1234L).getCreationTimeSeconds());

        assertEquals(Script.SIG_SIZE + 33, extend(address).getNumberOfBytesRequiredToSpend(33, null));
        assertEquals(Script.SIG_SIZE + 65, extend(address).getNumberOfBytesRequiredToSpend(0, null));
        assertEquals(Script.SIG_SIZE, extend(rawKey).getNumberOfBytesRequiredToSpend(33, null));
        assertEquals(Script.SIG_SIZE + 1, extend(multiSig).getNumberOfBytesRequiredToSpend(33, null));
        assertThrows(IllegalArgumentException.class,
                () -> extend(p2sh).getNumberOfBytesRequiredToSpend(33, null));
        assertEquals(Script.SIG_SIZE + multiSig.getProgram().length,
                extend(p2sh).getNumberOfBytesRequiredToSpend(33, multiSig));
        assertEquals(Script.SIG_SIZE + 33, extend(ordinal).getNumberOfBytesRequiredToSpend(33, null));
        assertEquals(Script.SIG_SIZE + 65, extend(ordinal).getNumberOfBytesRequiredToSpend(0, null));
        assertThrows(IllegalStateException.class,
                () -> extend(new ScriptBuilder().op(OP_TRUE).build()).getNumberOfBytesRequiredToSpend(33, null));
    }

    @Test
    void insertsSignaturesAtTheCorrectPositionForEachScriptType() {
        Script address = ScriptBuilder.createOutputScript(AddressEnhance.fromECkeyLite(KEY));
        Script rawKey = ScriptBuilder.createOutputScript(KEY);
        Script multiSig = ScriptBuilder.createMultiSigOutputScript(1, List.of(KEY));
        Script p2sh = ScriptBuilder.createP2SHOutputScript(multiSig);
        Script ordinal = OrdScriptBuilder.buildInscription(new Address(MainNetParams.get(), KEY.getPubKeyHash()),
                new byte[] {1}, "text/plain", null);

        Script addressInput = new ScriptBuilder().data(new byte[0]).data(KEY.getPubKey()).build();
        Script signedAddress = extend(address).getScriptSigWithSignature(addressInput, SIGNATURE, 0);
        assertArrayEquals(SIGNATURE, signedAddress.getChunks().get(0).data());
        assertArrayEquals(KEY.getPubKey(), signedAddress.getChunks().get(1).data());

        Script multiInput = new ScriptBuilder().data(new byte[0]).data(new byte[0]).build();
        Script signedMulti = extend(multiSig).getScriptSigWithSignature(multiInput, SIGNATURE, 0);
        assertEquals(2, signedMulti.getChunks().size());
        assertArrayEquals(SIGNATURE, signedMulti.getChunks().get(1).data());

        Script p2shInput = new ScriptBuilder().data(new byte[0]).data(new byte[0]).data(multiSig.getProgram()).build();
        Script signedP2sh = extend(p2sh).getScriptSigWithSignature(p2shInput, SIGNATURE, 0);
        assertArrayEquals(SIGNATURE, signedP2sh.getChunks().get(1).data());
        assertArrayEquals(multiSig.getProgram(), signedP2sh.getChunks().get(2).data());

        Script signedRaw = extend(rawKey).getScriptSigWithSignature(new ScriptBuilder().data(new byte[0]).build(), SIGNATURE, 0);
        assertArrayEquals(SIGNATURE, signedRaw.getChunks().get(0).data());

        Script signedOrdinal = extend(ordinal).getScriptSigWithSignature(addressInput, SIGNATURE, 0);
        assertArrayEquals(SIGNATURE, signedOrdinal.getChunks().get(0).data());

        Script signedUnknown = extend(new ScriptBuilder().op(OP_TRUE).build())
                .getScriptSigWithSignature(new ScriptBuilder().data(new byte[0]).build(), SIGNATURE, 0);
        assertArrayEquals(SIGNATURE, signedUnknown.getChunks().get(0).data());
    }

    private static ScriptExtend extend(Script script) {
        return new ScriptExtend(script.getProgram());
    }
}
