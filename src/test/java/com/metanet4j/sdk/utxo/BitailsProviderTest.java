package com.metanet4j.sdk.utxo;

import com.google.common.collect.Lists;
import com.metanet4j.base.util.JacksonUtil;
import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.utxo.impl.bitails.BitailsClient;
import com.metanet4j.sdk.utxo.impl.bitails.BitailsUtxoProvider;
import com.metanet4j.sdk.utxo.impl.bitails.dto.res.AddressBalanceResDto;
import io.bitcoinsv.bitcoinjsv.core.UTXO;
import io.bitcoinsv.bitcoinjsv.params.MainNetParams;
import java.util.List;
import org.junit.Test;

public class BitailsProviderTest {


    @Test
    public void getUtxoByAddress(){
        BitailsUtxoProvider bitailsProvider =new BitailsUtxoProvider();

        List<UTXO> utxos = bitailsProvider.listUxtos(Lists.newArrayList(AddressEnhance.fromBase58(MainNetParams.get(),
                TestData.utxoAddress)));
        utxos.stream().forEach(o->{
            System.out.println(JacksonUtil.obj2String(o));
        });
    }

    @Test
    public void getBalanceByAddress(){
        AddressBalanceResDto addressBalanceResDto =
                BitailsClient.getAddressBalanceResDto(TestData.utxoAddress);

        System.out.println(addressBalanceResDto);

    }

}
