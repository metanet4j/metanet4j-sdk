package com.metanet4j.sdk.crypto;

import cn.hutool.crypto.digest.DigestAlgorithm;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.crypto.digest.Digester;

public class DigestUtilExtend extends DigestUtil {

    public static byte[] sha512(byte[] data) {
        return new Digester(DigestAlgorithm.SHA512).digest(data);
    }
}
