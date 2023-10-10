package com.metanet4j.sdk.utxo.impl.gorillapool;

import cn.hutool.core.codec.Base64;
import com.google.common.collect.Lists;
import com.metanet4j.sdk.utxo.UTXOProvider;
import com.metanet4j.sdk.utxo.impl.gorillapool.dto.res.Txo;
import io.bitcoinsv.bitcoinjsv.core.Coin;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import io.bitcoinsv.bitcoinjsv.core.UTXO;
import io.bitcoinsv.bitcoinjsv.script.Script;

import java.util.List;

/**
 * see  https://ordinals.gorillapool.io/api/utxos/origin/{origin}
 */
public class OrdinalsOriginGorillaUtxoProvider implements UTXOProvider<String, UTXO> {

    @Override
    public List<UTXO> listUxtos(List<String> list) {
        Txo txo = GorillaClient.GetTxoByOrigin(list.get(0));
        UTXO utxo = new UTXO(Sha256Hash.wrap(txo.getTxid()), txo.getVout(), Coin.valueOf(txo.getSatoshis()),
                0, false, new Script(Base64.decode(txo.getScript())));
        return Lists.newArrayList(utxo);
    }
}
