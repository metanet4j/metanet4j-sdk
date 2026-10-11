package com.metanet4j.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.metanet4j.bsv.primitives.PrivateKey;
import com.metanet4j.bsv.transaction.Transaction;
import com.metanet4j.sdk.exception.InvalidKeyException;
import com.metanet4j.sdk.exception.SignErrorException;
import com.metanet4j.sdk.signers.ExtendForSignTransaction;
import org.junit.jupiter.api.Test;

/**
 * 四个 **公开构造器** 的契约（§82.4 第 ① 项：先清构造器）。
 *
 * <p>§82 刷新的清单里有 6 个未覆盖构造器，按 §79.3 判可见性后分三类：
 *
 * <ul>
 *   <li>**公开且可调用（4 个）**：本类覆盖——`InvalidKeyException(String)`、
 *       `SignErrorException()`、`ExtendForSignTransaction(Transaction)`、`PrivateKey(bsv PrivateKey)`；</li>
 *   <li>**private 且无调用点（1 个）**：`ScriptExtend()` 是工具类**防实例化**的私有构造器，
 *       属 §76.3 所说的"用空实现表达设计意图"，**保留、不测**（无调用点可触达）；</li>
 *   <li>**隐式默认构造器（1 个）**：`OrdinalsTransactionSigner()` 由编译器生成、无显式声明，
 *       属 §52 类别 A（隐式/合成方法）。</li>
 * </ul>
 */
class PublicConstructorContractTest {

    @Test
    void constructsBothExceptionTypesWithTheirIntendedPayload() {
        SignErrorException signError = new SignErrorException();
        assertNotNull(signError, "SignErrorException 应可无参构造");

        InvalidKeyException invalidKey = new InvalidKeyException("bad key");
        assertEquals("bad key", invalidKey.getMessage(), "InvalidKeyException 应保留消息");
    }

    @Test
    void constructsTheSignerOverAPartialTransactionAndWrapsABsvPrivateKey() {
        Transaction partial = new Transaction();
        ExtendForSignTransaction signer = new ExtendForSignTransaction(partial);
        assertNotNull(signer, "应能就部分交易构造签名器");

        PrivateKey raw = PrivateKey.fromString("01", 16);
        com.metanet4j.sdk.PrivateKey wrapped = new com.metanet4j.sdk.PrivateKey(raw);
        assertSame(raw, wrapped.getKey(), "包装构造器应原样持有底层 bsv 私钥");
    }
}
