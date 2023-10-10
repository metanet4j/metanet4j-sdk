package com.metanet4j.sdk.utxo;

import io.bitcoinsv.bitcoinjsv.core.UTXO;

import java.util.List;

@FunctionalInterface
public interface UTXOProvider<T, R extends UTXO> {

    List<R> listUxtos(List<T> list);

}
