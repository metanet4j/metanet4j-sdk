package com.metanet4j.sdk.utxo;

import com.google.common.collect.Lists;
import com.metanet4j.base.util.JacksonUtil;
import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.utxo.impl.bitails.BitailsClient;
import com.metanet4j.sdk.utxo.impl.bitails.BitailsUtxoProvider;
import com.metanet4j.sdk.utxo.impl.bitails.dto.res.AddressBalanceResDto;
import com.metanet4j.base.bsv.BsvUtxo;
import com.metanet4j.base.bsv.BsvNetwork;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("external")
public class BitailsProviderTest {


    @Test
    public void getUtxoByAddress(){
        BitailsUtxoProvider bitailsProvider =new BitailsUtxoProvider();

        List<BsvUtxo> utxos = bitailsProvider.listUxtos(Lists.newArrayList(AddressEnhance.fromBase58(BsvNetwork.MAINNET,
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
