package com.metanet4j.sdk.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.metanet4j.sdk.PublicKey;
import com.metanet4j.sdk.remote.RemoteSignType;
import com.metanet4j.base.bsv.BsvAmount;
import com.metanet4j.base.bsv.BsvHashBytes;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

class PreSignHashContextContractTest {

    @Test
    void roundTripsRequiredFieldsWithoutOptionalFeeOrPublicKey() {
        PreSignHashContext context = new PreSignHashContext();
        context.setSignAddress("local-address");
        context.setSigB64("AQID");
        context.setPreSignHash(BsvHashBytes.fromDisplayHex("1".repeat(64)));
        context.setRemoteSignType(RemoteSignType.CURRENT);

        LinkedHashMap<String, Object> map = context.toMap();
        assertFalse(map.containsKey("fee"));
        assertFalse(map.containsKey("publicKey"));
        PreSignHashContext restored = new PreSignHashContext().fromMap(map);
        assertEquals(context.getSignAddress(), restored.getSignAddress());
        assertEquals(context.getSigB64(), restored.getSigB64());
        assertEquals(context.getPreSignHash(), restored.getPreSignHash());
        assertEquals(context.getRemoteSignType(), restored.getRemoteSignType());
        assertNull(restored.getFee());
        assertNull(restored.getPublicKey());
    }

    @Test
    void roundTripsOptionalFeeAndPublicKey() {
        PreSignHashContext context = new PreSignHashContext();
        context.setSignAddress("local-address");
        context.setSigB64("AQID");
        context.setPreSignHash(BsvHashBytes.fromDisplayHex("2".repeat(64)));
        context.setRemoteSignType(RemoteSignType.PAYMENT);
        context.setFee(BsvAmount.valueOf(42));
        context.setPublicKey(com.metanet4j.sdk.PrivateKey.of(BigInteger.TWO).getPublicKey());

        LinkedHashMap<String, Object> map = context.toMap();
        map.put("fee", Integer.valueOf(42));
        PreSignHashContext restored = new PreSignHashContext().fromMap(map);
        assertEquals(BsvAmount.valueOf(42), restored.getFee());
        assertEquals(context.getPublicKey().getPubKeyHex(), restored.getPublicKey().getPubKeyHex());
        assertEquals(context.getPreSignHash(), restored.getPreSignHash());
        assertEquals(RemoteSignType.PAYMENT, restored.getRemoteSignType());
    }

    @Test
    void preservesAnAbsentPreSignHashBeforeSigning() {
        PreSignHashContext context = new PreSignHashContext();
        context.setRemoteSignType(RemoteSignType.CURRENT);
        PreSignHashContext restored = new PreSignHashContext().fromMap(context.toMap());
        assertNull(restored.getPreSignHash());
        assertNull(restored.getFee());
        assertNull(restored.getPublicKey());
    }
}
