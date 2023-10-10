package com.metanet4j.sdk.utxo.impl.bitails;

import com.metanet4j.sdk.utxo.UTXOProvider;
import com.metanet4j.sdk.utxo.impl.bitails.dto.res.AddressUnspentResDto;
import io.bitcoinsv.bitcoinjsv.core.AddressLite;
import io.bitcoinsv.bitcoinjsv.core.Coin;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import io.bitcoinsv.bitcoinjsv.core.UTXO;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * see https://api.bitails.io/swagger
 */
public class BitailsUtxoProvider implements UTXOProvider<AddressLite, UTXO> {

    @Override
    public List<UTXO> listUxtos(List<AddressLite> list) {
        List<UTXO> utxoList = new ArrayList<>();
        list.stream().forEach(addressLite -> {
            AddressUnspentResDto unspentResDto = BitailsClient.getAddressUnspentResDto(addressLite.toBase58(), 0, 100);
            List<UTXO> utxos = Optional.ofNullable(unspentResDto.getUnspent()).orElse(new ArrayList<>()).stream().map(o -> {
                UTXO utxo = new UTXO(Sha256Hash.wrap(o.getTxid()), o.getVout(), Coin.valueOf(o.getSatoshis()),
                        0, false, null, unspentResDto.getAddress());
                return utxo;
            }).collect(Collectors.toList());

            utxoList.addAll(utxos);

        });

        return utxoList;


    }


}
