package com.metanet4j.sdk.utils;

import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import io.bitcoinsv.bitcoinjsv.core.Utils;
import io.bitcoinsv.bitcoinjsv.core.VarInt;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import lombok.experimental.UtilityClass;

@UtilityClass
public class UtilsExtend extends Utils {
    public static byte[] formatMessageForSigning(byte[] messageBytes) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(BITCOIN_SIGNED_MESSAGE_HEADER_BYTES.length);
        bos.writeBytes(BITCOIN_SIGNED_MESSAGE_HEADER_BYTES);
        VarInt size = new VarInt(messageBytes.length);
        bos.writeBytes(size.encode());
        bos.writeBytes(messageBytes);
        return bos.toByteArray();
    }


    public static byte[] toHashByte(String content) {
        return toHashByte(content, StandardCharsets.UTF_8);
    }

    public static byte[] toHashByte(String content, Charset charset) {
        return toHashByte(content.getBytes(charset));
    }

    public static byte[] toHashByte(byte[] bytes) {
        return Sha256Hash.of(bytes).getBytes();
    }


    public static String toHash(String content) {
        return toHash(content, StandardCharsets.UTF_8);
    }

    public static String toHash(String content, Charset charset) {
        return toHash(content.getBytes(charset));
    }

    public static String toHash(byte[] bytes) {
        return Sha256Hash.of(bytes).toString();
    }

}
