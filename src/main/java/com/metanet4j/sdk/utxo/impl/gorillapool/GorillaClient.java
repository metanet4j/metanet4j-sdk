package com.metanet4j.sdk.utxo.impl.gorillapool;

import cn.hutool.core.util.StrUtil;
import com.metanet4j.sdk.utxo.impl.HttpClientFactory;
import com.metanet4j.sdk.utxo.impl.gorillapool.dto.res.Txo;

import java.net.http.HttpRequest;

import static com.metanet4j.sdk.utxo.impl.HttpClientFactory.send;

public class GorillaClient {

    public static final String BASE_URL = "https://v3.ordinals.gorillapool.io";



    public static Txo GetTxoByOrigin(String Origin) {

        String format = StrUtil.format(BASE_URL + "/api/txos/{}?script=true",
                Origin);
        HttpRequest getHttpRequest = HttpClientFactory.createGetHttpRequest(format, 1000 * 60);

        return send(getHttpRequest, Txo.class);
    }


}
