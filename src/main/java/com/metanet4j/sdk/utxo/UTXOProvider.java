package com.metanet4j.sdk.utxo;

import com.metanet4j.base.bsv.BsvUtxo;
import java.util.List;

/**
 * 未花费输出提供者：按地址批量查询 UTXO。
 *
 * <p>取代迁移前的 UTXO 值类型：泛型上界为 base 的业务值类型 {@link BsvUtxo}
 * （新 SDK 的 {@code compat.Utxo} 只有 {@code JsonUtxo} 记录与 {@code fromUtxo} 入口，
 * 不是 UTXO 模型，不能作为上界）。
 * 地址参数类型由 {@code AddressLite} 换为业务地址 {@code AddressEnhance}。
 */
@FunctionalInterface
public interface UTXOProvider<T, R extends BsvUtxo> {

    /** 按地址列表查询未花费输出。 */
    List<R> listUxtos(List<T> list);
}
