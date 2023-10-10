package com.metanet4j.sdk.utils;

import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import io.bitcoinsv.bitcoinjsv.core.Utils;
import io.bitcoinsv.bitcoinjsv.core.VarInt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class UtilsExtend extends Utils {
    public static byte[] formatMessageForSigning(byte[] messageBytes) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(BITCOIN_SIGNED_MESSAGE_HEADER_BYTES.length);
            bos.write(BITCOIN_SIGNED_MESSAGE_HEADER_BYTES);
            VarInt size = new VarInt(messageBytes.length);
            bos.write(size.encode());
            bos.write(messageBytes);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);  // Cannot happen.
        }
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
