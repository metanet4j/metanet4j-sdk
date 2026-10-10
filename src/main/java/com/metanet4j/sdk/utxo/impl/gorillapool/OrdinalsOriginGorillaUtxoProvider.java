package com.metanet4j.sdk.utxo.impl.gorillapool;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import com.metanet4j.sdk.utxo.UTXOProvider;
import com.metanet4j.sdk.utxo.impl.gorillapool.dto.res.Txo;
import com.metanet4j.base.bsv.BsvHashBytes;
import com.metanet4j.base.bsv.BsvUtxo;

import java.util.List;

/**
 * see  https://ordinals.gorillapool.io/api/utxos/origin/{origin}
 */
public class OrdinalsOriginGorillaUtxoProvider implements UTXOProvider<String, BsvUtxo> {

    private final String baseUrl;

    public OrdinalsOriginGorillaUtxoProvider() {
        this(GorillaClient.BASE_URL);
    }

    public OrdinalsOriginGorillaUtxoProvider(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public List<BsvUtxo> listUxtos(List<String> list) {
        Txo txo = GorillaClient.GetTxoByOrigin(baseUrl, list.get(0));
        com.metanet4j.bsv.script.Script lockingScript =
                com.metanet4j.bsv.script.Script.fromBinary(Base64.decode(txo.getScript()));
        BsvUtxo utxo = new BsvUtxo(
                BsvHashBytes.fromDisplayHex(StrUtil.isNotEmpty(txo.getSpend()) ? txo.getSpend() : txo.getTxid()),
                txo.getVout(), (long) txo.getSatoshis(), 0, false, null, lockingScript.toUint8Array());
        return Lists.newArrayList(utxo);
    }
}
