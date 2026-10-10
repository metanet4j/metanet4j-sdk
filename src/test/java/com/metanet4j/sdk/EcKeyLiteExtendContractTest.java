package com.metanet4j.sdk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metanet4j.base.bsv.BsvHashBytes;
import com.metanet4j.bsv.primitives.BigNumber;
import com.metanet4j.bsv.primitives.PublicKey;
import com.metanet4j.bsv.primitives.Utils;
import com.metanet4j.sdk.utils.UtilsExtend;
import java.math.BigInteger;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * {@link EcKeyLiteExtend} 的签名与恢复契约（V05 相关）。
 *
 * <p>迁移要点：摘要改 {@link BsvHashBytes}；恢复公钥改用新 SDK 的
 * {@code PublicKey.fromMsgHashAndCompactSignature}（直接吃 65 字节 compact 签名，无需手工拆 r／s）；
 * 验签改用本类新增的 {@link EcKeyLiteExtend#verify(BsvHashBytes, byte[])}；
 * 非压缩实例改用新增的 {@link EcKeyLiteExtend#fromPrivateUncompressed(BigInteger)}
 * （局限：新 SDK 未公开非压缩公钥编码，该标志只作用于 compact 头与恢复校验）。
 */
class EcKeyLiteExtendContractTest {

    @Test
    void signsBinaryMessageAndRecoversThePublicKey() {
        EcKeyLiteExtend key = EcKeyLiteExtend.fromPrivate(BigInteger.TWO);
        byte[] message = {0, (byte) 0xff, 42};
        BsvHashBytes signingHash = BsvHashBytes.twiceOf(UtilsExtend.formatMessageForSigning(message));

        String signature = key.signMessage(message);
        assertEquals(key.signHash(signingHash), signature);
        assertEquals(signature, key.signMessage(message, null));
        assertTrue(key.isCompressed());
        byte[] compact = Base64.getDecoder().decode(signature);
        assertEquals(65, compact.length);
        int recovery = Byte.toUnsignedInt(compact[0]) - 27 - 4;
        assertTrue(recovery >= 0 && recovery < 4);

        PublicKey recovered = PublicKey.fromMsgHashAndCompactSignature(messageHash(signingHash), compact);
        assertArrayEquals(toBytes(key.getPublicKey().toDER()), toBytes(recovered.toDER()));
        assertTrue(key.verify(signingHash, compact));

        assertNotEquals(signature, key.signMessage(new byte[] {0, (byte) 0xfe, 42}));
        assertFalse(key.verify(BsvHashBytes.of(new byte[] {1}), compact));
    }

    @Test
    void compactSignaturePreservesUncompressedPublicKeyFormat() {
        EcKeyLiteExtend key = EcKeyLiteExtend.fromPrivateUncompressed(BigInteger.TWO);
        byte[] message = {1, 0, (byte) 0xff};
        BsvHashBytes hash = BsvHashBytes.twiceOf(UtilsExtend.formatMessageForSigning(message));

        assertFalse(key.isCompressed());
        byte[] compact = Base64.getDecoder().decode(key.signHash(hash));
        int header = Byte.toUnsignedInt(compact[0]);
        assertTrue(header >= 27 && header <= 30, "非压缩实例的 compact 头应落在 27—30");

        PublicKey recovered = PublicKey.fromMsgHashAndCompactSignature(messageHash(hash), compact);
        assertArrayEquals(toBytes(key.getPublicKey().toDER()), toBytes(recovered.toDER()));
    }

    @Test
    void rejectsSignatureWhenPrivateAndPublicKeysDoNotMatch() {
        EcKeyLiteExtend inconsistent = new EcKeyLiteExtend(BigInteger.TWO,
                PublicKey.fromPrivateKey(com.metanet4j.bsv.primitives.PrivateKey.fromString("3", 16)));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> inconsistent.signMessage(new byte[] {1, 2, 3}));
        assertTrue(error.getMessage().contains("recoverable key"));
    }

    private static BigNumber messageHash(BsvHashBytes hash) {
        return new BigNumber(Utils.toArray(hash.getBytes()));
    }

    private static byte[] toBytes(java.util.List<?> values) {
        byte[] bytes = new byte[values.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ((Number) values.get(i)).intValue();
        }
        return bytes;
    }
}
