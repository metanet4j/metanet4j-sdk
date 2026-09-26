package com.metanet4j.sdk.utxo.impl.gorillapool;

import cn.hutool.core.util.StrUtil;
import com.metanet4j.sdk.utxo.impl.HttpClientFactory;
import com.metanet4j.sdk.utxo.impl.gorillapool.dto.res.Txo;

import java.net.http.HttpRequest;

import static com.metanet4j.sdk.utxo.impl.HttpClientFactory.send;

public class GorillaClient {

    public static final String BASE_URL = "https://ordinals.gorillapool.io";


    public static Txo GetTxoByOrigin(String Origin) {
        return GetTxoByOrigin(BASE_URL, Origin);
    }

    public static Txo GetTxoByOrigin(String baseUrl, String origin) {
        String format = StrUtil.format(baseUrl + "/api/txos/{}?script=true",
                origin);
        HttpRequest getHttpRequest = HttpClientFactory.createGetHttpRequest(format, 1000 * 60);

        return send(getHttpRequest, Txo.class);
    }


}
