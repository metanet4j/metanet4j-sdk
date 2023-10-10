package com.metanet4j.sdk.utxo.impl.gorillapool;

import cn.hutool.core.util.StrUtil;
import com.metanet4j.sdk.utxo.impl.HttpClientFactory;
import com.metanet4j.sdk.utxo.impl.gorillapool.dto.res.Inscription;
import com.metanet4j.sdk.utxo.impl.gorillapool.dto.res.Txo;

import java.net.http.HttpRequest;

import static com.metanet4j.sdk.utxo.impl.HttpClientFactory.send;

public class GorillaClient {

    public static final String BASE_URL = "https://ordinals.gorillapool.io";

    public static Inscription GetByOrigin(String Origin) {

        String format = StrUtil.format(BASE_URL + "/api/inscriptions/origin/{}",
                Origin);
        HttpRequest getHttpRequest = HttpClientFactory.createGetHttpRequest(format, 1000 * 60);

        return send(getHttpRequest, Inscription.class);
    }


    public static Txo GetTxoByOrigin(String Origin) {

        String format = StrUtil.format(BASE_URL + "/api/utxos/origin/{}",
                Origin);
        HttpRequest getHttpRequest = HttpClientFactory.createGetHttpRequest(format, 1000 * 60);

        return send(getHttpRequest, Txo.class);
    }


}
