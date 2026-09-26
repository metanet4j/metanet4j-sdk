package com.metanet4j.sdk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metanet4j.sdk.utils.UtilsExtend;
import io.bitcoinsv.bitcoinjsv.core.ECKeyLite;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import io.bitcoinsv.bitcoinjsv.ecc.ECDSASignature;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class EcKeyLiteExtendContractTest {

    @Test
    void signsBinaryMessageAndRecoversThePublicKey() {
        EcKeyLiteExtend key = EcKeyLiteExtend.fromPrivate(BigInteger.TWO);
        byte[] message = {0, (byte) 0xff, 42};
        Sha256Hash signingHash = Sha256Hash.twiceOf(UtilsExtend.formatMessageForSigning(message));

        String signature = key.signMessage(message);
        assertEquals(key.signHash(signingHash), signature);
        assertEquals(signature, key.signMessage(message, null));
        assertTrue(key.isCompressed());
        byte[] compact = Base64.getDecoder().decode(signature);
        assertEquals(65, compact.length);
        int recovery = Byte.toUnsignedInt(compact[0]) - 27 - 4;
        assertTrue(recovery >= 0 && recovery < 4);
        ECDSASignature decoded = new ECDSASignature(
                new BigInteger(1, Arrays.copyOfRange(compact, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(compact, 33, 65)));
        ECKeyLite recovered = ECKeyLite.recoverFromSignature(recovery, decoded, signingHash, true);
        assertArrayEquals(key.getPubKey(), recovered.getPubKey());
        assertTrue(key.verify(signingHash, decoded));

        assertNotEquals(signature, key.signMessage(new byte[] {0, (byte) 0xfe, 42}));
        assertFalse(key.verify(Sha256Hash.of(new byte[] {1}), decoded));
    }
}
