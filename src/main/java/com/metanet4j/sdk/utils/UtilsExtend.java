package com.metanet4j.sdk.utils;

import com.metanet4j.base.bsv.BsvHashBytes;
import com.metanet4j.bsv.primitives.Utils;
import com.metanet4j.bsv.primitives.Writer;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import lombok.experimental.UtilityClass;

/**
 * BSM 消息签名前缀与单次 SHA-256 工具。
 *
 * <p>旧实现继承 bitcoinj 的 {@code Utils} 以复用 {@code BITCOIN_SIGNED_MESSAGE_HEADER_BYTES}；
 * 现改为自持常量并走新 SDK（com.metanet4j.bsv）的 {@code Writer} 与
 * {@link BsvHashBytes}。{@code toHash} 仍输出旧库 {@code Sha256Hash.toString()} 的显示序 hex。
 */
@UtilityClass
public class UtilsExtend {

    /** Bitcoin 签名消息头：0x18 + "Bitcoin Signed Message:\n"。 */
    private static final byte[] BITCOIN_SIGNED_MESSAGE_HEADER_BYTES =
            "Bitcoin Signed Message:\n".getBytes(StandardCharsets.UTF_8);

    /** 前缀 + CompactSize 长度 + 消息体（BSM 待签名数据）。 */
    public static byte[] formatMessageForSigning(byte[] messageBytes) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(BITCOIN_SIGNED_MESSAGE_HEADER_BYTES.length);
        bos.writeBytes(BITCOIN_SIGNED_MESSAGE_HEADER_BYTES);
        bos.writeBytes(new Writer().writeVarIntNum(messageBytes.length)
                .toUint8Array().toByteArray());
        bos.writeBytes(messageBytes);
        return bos.toByteArray();
    }

    public static byte[] toHashByte(String content) {
        return toHashByte(content, StandardCharsets.UTF_8);
    }

    public static byte[] toHashByte(String content, Charset charset) {
        return toHashByte(content.getBytes(charset));
    }

    /** 单次 SHA-256 的内部字节（非反转）。 */
    public static byte[] toHashByte(byte[] bytes) {
        return BsvHashBytes.of(bytes).getBytes();
    }

    public static String toHash(String content) {
        return toHash(content, StandardCharsets.UTF_8);
    }

    public static String toHash(String content, Charset charset) {
        return toHash(content.getBytes(charset));
    }

    /** 单次 SHA-256 的显示序 hex（与旧库 {@code Sha256Hash.toString()} 一致）。 */
    public static String toHash(byte[] bytes) {
        return BsvHashBytes.of(bytes).toString();
    }

    /** 与旧库 {@code Utils.HEX.encode} 等价。 */
    public static String toHex(byte[] bytes) {
        return Utils.toHex(bytes);
    }

    /** 与旧库 {@code Utils.HEX.decode} 等价。 */
    public static byte[] fromHex(String hex) {
        return toBytes(Utils.toArray(hex, "hex"));
    }

    private static byte[] toBytes(java.util.List<Object> values) {
        byte[] bytes = new byte[values.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ((Number) values.get(i)).intValue();
        }
        return bytes;
    }
}
