package com.metanet4j.sdk.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.bitcoinsv.bitcoinjsv.core.ECKey;
import io.bitcoinsv.bitcoinjsv.script.Script;
import io.bitcoinsv.bitcoinjsv.script.ScriptBuilder;
import io.bitcoinsv.bitcoinjsv.temp.RedeemData;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class RedeemDataExtendContractTest {

    @Test
    void sortsKeysAndKeepsAnIndependentList() {
        ECKey first = ECKey.fromPrivate(BigInteger.valueOf(3));
        ECKey second = ECKey.fromPrivate(BigInteger.TWO);
        Script script = new ScriptBuilder().build();
        List<ECKey> input = new ArrayList<>(List.of(first, second));
        List<ECKey> expected = new ArrayList<>(input);
        expected.sort(ECKey.PUBKEY_COMPARATOR);
        Collections.reverse(input);

        RedeemDataExtend data = RedeemDataExtend.of(input, script);
        input.clear();

        assertEquals(expected, data.keys);
        assertNotSame(input, data.keys);
        assertSame(script, data.redeemScript);
        assertSame(expected.stream().filter(ECKey::hasPrivKey).findFirst().orElseThrow(), data.getFullKey());
    }

    @Test
    void adaptsLibraryRedeemDataAndSelectsAvailablePrivateKey() {
        ECKey privateKey = ECKey.fromPrivate(BigInteger.TWO);
        ECKey publicOnly = ECKey.fromPublicOnly(ECKey.fromPrivate(BigInteger.valueOf(3)).getPubKey());
        Script script = new ScriptBuilder().build();
        RedeemData libraryData = RedeemData.of(List.of(publicOnly, privateKey), script);

        RedeemDataExtend data = RedeemDataExtend.of(libraryData);
        assertEquals(2, data.keys.size());
        assertSame(script, data.redeemScript);
        assertSame(privateKey, data.getFullKey());
        assertNull(RedeemDataExtend.of(List.of(publicOnly), script).getFullKey());
        assertNull(RedeemDataExtend.of(List.of(), script).getFullKey());
    }

    @Test
    void singleKeyFactoryReturnsNullForUnknownKey() {
        Script script = new ScriptBuilder().build();
        assertNull(RedeemDataExtend.of((ECKey) null, script));
        ECKey key = ECKey.fromPrivate(BigInteger.valueOf(3));
        assertSame(key, RedeemDataExtend.of(key, script).getFullKey());
    }
}
