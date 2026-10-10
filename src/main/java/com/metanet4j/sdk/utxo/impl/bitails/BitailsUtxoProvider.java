package com.metanet4j.sdk.utxo.impl.bitails;

import com.metanet4j.base.bsv.BsvHashBytes;
import com.metanet4j.base.bsv.BsvUtxo;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.utxo.UTXOProvider;
import com.metanet4j.sdk.utxo.impl.bitails.dto.res.AddressUnspentResDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * see https://api.bitails.io/swagger
 */
public class BitailsUtxoProvider implements UTXOProvider<AddressEnhance, BsvUtxo> {

    private final String baseUrl;

    public BitailsUtxoProvider() {
        this(BitailsClient.BASE_URL);
    }

    public BitailsUtxoProvider(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public List<BsvUtxo> listUxtos(List<AddressEnhance> list) {
        List<BsvUtxo> utxoList = new ArrayList<>();
         list.stream().forEach(address -> {
            AddressUnspentResDto unspentResDto = BitailsClient.getAddressUnspentResDto(baseUrl, address.toBase58(), 0, 100);
            List<BsvUtxo> utxos = Optional.ofNullable(unspentResDto.getUnspent()).orElse(new ArrayList<>()).stream().map(o -> {
                return new BsvUtxo(BsvHashBytes.fromDisplayHex(o.getTxid()), o.getVout(),
                        (long) o.getSatoshis(), 0, false, unspentResDto.getAddress(), null);
            }).collect(Collectors.toList());

            utxoList.addAll(utxos);

        });

        return utxoList;


    }


}
