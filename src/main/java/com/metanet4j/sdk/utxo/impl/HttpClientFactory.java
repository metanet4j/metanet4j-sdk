package com.metanet4j.sdk.utxo.impl;

import cn.hutool.core.util.StrUtil;
import com.google.common.base.Charsets;
import com.metanet4j.base.util.JacksonUtil;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class HttpClientFactory {

    private HttpClientFactory(){};
    private static volatile HttpClient httpClient=null;

    public static HttpClient createHttpClient(){
        if (httpClient==null){
            synchronized (HttpClientFactory.class){
                if(httpClient==null){
                    httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(1000 * 60))
                            .version(Version.HTTP_1_1).build();
                }
            }

        }
        return httpClient;
    }


    public static HttpRequest createGetHttpRequest(String uri,long timeout){
        try {
            HttpRequest httpRequest =
                   HttpRequest.newBuilder().uri(new URI(uri)).timeout(Duration.ofMillis(timeout)).build();
            return httpRequest;
        } catch (URISyntaxException e) {
            e.printStackTrace();
            throw new RuntimeException("createGetHttpRequest fail");
        }
    }

    public static HttpRequest createPostHttpRequest(String uri,long timeout,String raw){
        try {
            HttpRequest httpRequest =
                    HttpRequest.newBuilder()
                            .uri(new URI(uri))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(raw))
                            .timeout(Duration.ofMillis(timeout)).build();
            return httpRequest;
        } catch (URISyntaxException e) {
            e.printStackTrace();
            throw new RuntimeException("createGetHttpRequest fail");
        }
    }

    public static <T> T send(HttpRequest request, Class<T> tClass) {
        try {
            HttpClient client = HttpClientFactory.createHttpClient();
            HttpResponse<String> response = client
                    .send(request, responseInfo -> HttpResponse.BodySubscribers.ofString(Charsets.UTF_8));
            if (response != null && StrUtil.isNotEmpty(response.body())) {
                T t = JacksonUtil
                        .string2Obj(response.body(), tClass);

                if (t == null) {
                    throw new RuntimeException("send fail,the url is " + request.uri() + ",the body is " + response.body());
                }
                return t;
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }


}
