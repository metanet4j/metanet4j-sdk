package com.metanet4j.sdk.utxo.impl.bitails;

import cn.hutool.core.util.StrUtil;
import com.metanet4j.base.util.JacksonUtil;
import com.metanet4j.sdk.utxo.impl.HttpClientFactory;
import com.metanet4j.sdk.utxo.impl.bitails.dto.req.TransactionBroadcastReqDto;
import com.metanet4j.sdk.utxo.impl.bitails.dto.res.AddressBalanceResDto;
import com.metanet4j.sdk.utxo.impl.bitails.dto.res.AddressUnspentResDto;

import java.net.http.HttpRequest;

import static com.metanet4j.sdk.utxo.impl.HttpClientFactory.send;

public class BitailsClient {

    public static final String BASE_URL = "https://api.bitails.io";

    public static AddressUnspentResDto getAddressUnspentResDto(String address, int from, int limit) {

        String format = StrUtil.format(BASE_URL + "/address/{}/unspent?from={}&limit={}",
                address, from, limit);
        HttpRequest getHttpRequest = HttpClientFactory.createGetHttpRequest(format, 1000 * 60);

        return send(getHttpRequest, AddressUnspentResDto.class);
    }




    public static AddressBalanceResDto getAddressBalanceResDto(String address) {

        String format = StrUtil.format(BASE_URL + "/address/{}/balance",
                address);
        HttpRequest getHttpRequest = HttpClientFactory.createGetHttpRequest(format, 1000 * 60);

        return send(getHttpRequest, AddressBalanceResDto.class);
    }


    public static String broadcast(String raw) {
        TransactionBroadcastReqDto transactionBroadcastReqDto =new TransactionBroadcastReqDto();
        transactionBroadcastReqDto.setRaw(raw);
        return broadcast(transactionBroadcastReqDto);
    }

    public static String broadcast(TransactionBroadcastReqDto transactionBroadcastReqDto) {

        String uri = BASE_URL + "/tx/broadcast";

        HttpRequest getHttpRequest = HttpClientFactory.createPostHttpRequest(uri, 1000 * 60*3,
                JacksonUtil.obj2String(transactionBroadcastReqDto));

        return send(getHttpRequest, String.class);
    }






}
