package com.metanet4j.sdk.utxo.impl.gorillapool.dto.res;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;

@NoArgsConstructor
@Data
public class Txo {

    @JsonProperty("txid")
    private String txid;
    @JsonProperty("vout")
    private Integer vout;
    @JsonProperty("outpoint")
    private String outpoint;
    @JsonProperty("satoshis")
    private Integer satoshis;
    @JsonProperty("accSats")
    private Integer accSats;
    @JsonProperty("owner")
    private String owner;
    @JsonProperty("script")
    private String script;
    @JsonProperty("spend")
    private String spend;
    @JsonProperty("origin")
    private OriginDTO origin;
    @JsonProperty("height")
    private Integer height;
    @JsonProperty("idx")
    private Integer idx;
    @JsonProperty("data")
    private DataDTO data;

    @NoArgsConstructor
    @Data
    public static class OriginDTO {
        @JsonProperty("outpoint")
        private String outpoint;
        @JsonProperty("data")
        private DataDTO data;
        @JsonProperty("num")
        private Integer num;
        @JsonProperty("map")
        private MapDTO map;
        @JsonProperty("claims")
        private List<ClaimsDTO> claims;

        @NoArgsConstructor
        @Data
        public static class DataDTO {
            @JsonProperty("types")
            private List<String> types;
            @JsonProperty("insc")
            private InscDTO insc;
            @JsonProperty("map")
            private MapDTO map;
            @JsonProperty("b")
            private BDTO b;
            @JsonProperty("sigma")
            private List<SigmaDTO> sigma;
            @JsonProperty("list")
            private ListDTO list;
            @JsonProperty("bsv20")
            private Bsv20DTO bsv20;

            @NoArgsConstructor
            @Data
            public static class InscDTO {
                @JsonProperty("json")
                private String json;
                @JsonProperty("text")
                private String text;
                @JsonProperty("words")
                private List<String> words;
                @JsonProperty("file")
                private FileDTO file;

                @NoArgsConstructor
                @Data
                public static class FileDTO {
                    @JsonProperty("hash")
                    private String hash;
                    @JsonProperty("size")
                    private Integer size;
                    @JsonProperty("type")
                    private String type;
                }
            }

            @NoArgsConstructor
            @Data
            public static class MapDTO extends LinkedHashMap<String, Object> {

            }

            @NoArgsConstructor
            @Data
            public static class BDTO {
                @JsonProperty("hash")
                private String hash;
                @JsonProperty("size")
                private Integer size;
                @JsonProperty("type")
                private String type;
            }

            @NoArgsConstructor
            @Data
            public static class ListDTO {
                @JsonProperty("payout")
                private String payout;
                @JsonProperty("price")
                private Integer price;
            }

            @NoArgsConstructor
            @Data
            public static class Bsv20DTO {
                @JsonProperty("implied")
                private Boolean implied;
                @JsonProperty("status")
                private Integer status;
                @JsonProperty("amt")
                private String amt;
                @JsonProperty("sym")
                private String sym;
                @JsonProperty("tick")
                private String tick;
                @JsonProperty("op")
                private String op;
                @JsonProperty("p")
                private String p;
                @JsonProperty("id")
                private String id;
            }

            @NoArgsConstructor
            @Data
            public static class SigmaDTO {
                @JsonProperty("algorithm")
                private String algorithm;
                @JsonProperty("address")
                private String address;
                @JsonProperty("signature")
                private String signature;
                @JsonProperty("vin")
                private Integer vin;
                @JsonProperty("valid")
                private boolean valid;
            }
        }

        @NoArgsConstructor
        @Data
        public static class MapDTO extends LinkedHashMap<String, Object> {

        }

        @NoArgsConstructor
        @Data
        public static class ClaimsDTO {
            @JsonProperty("sub")
            private String sub;
            @JsonProperty("type")
            private String type;
            @JsonProperty("value")
            private String value;
        }
    }

    @NoArgsConstructor
    @Data
    public static class DataDTO {
        @JsonProperty("types")
        private List<String> types;
        @JsonProperty("insc")
        private InscDTO insc;
        @JsonProperty("map")
        private MapDTO map;
        @JsonProperty("b")
        private BDTO b;
        @JsonProperty("sigma")
        private List<SigmaDTO> sigma;
        @JsonProperty("list")
        private ListDTO list;
        @JsonProperty("bsv20")
        private Bsv20DTO bsv20;

        @NoArgsConstructor
        @Data
        public static class InscDTO {
            @JsonProperty("json")
            private String json;
            @JsonProperty("text")
            private String text;
            @JsonProperty("words")
            private List<String> words;
            @JsonProperty("file")
            private FileDTO file;

            @NoArgsConstructor
            @Data
            public static class FileDTO {
                @JsonProperty("hash")
                private String hash;
                @JsonProperty("size")
                private Integer size;
                @JsonProperty("type")
                private String type;
            }
        }

        @NoArgsConstructor
        @Data
        public static class MapDTO extends LinkedHashMap<String, Object> {

        }

        @NoArgsConstructor
        @Data
        public static class BDTO {
            @JsonProperty("hash")
            private String hash;
            @JsonProperty("size")
            private Integer size;
            @JsonProperty("type")
            private String type;
        }

        @NoArgsConstructor
        @Data
        public static class ListDTO {
            @JsonProperty("payout")
            private String payout;
            @JsonProperty("price")
            private Integer price;
        }

        @NoArgsConstructor
        @Data
        public static class Bsv20DTO {
            @JsonProperty("implied")
            private Boolean implied;
            @JsonProperty("status")
            private Integer status;
            @JsonProperty("amt")
            private String amt;
            @JsonProperty("sym")
            private String sym;
            @JsonProperty("tick")
            private String tick;
            @JsonProperty("op")
            private String op;
            @JsonProperty("p")
            private String p;
            @JsonProperty("id")
            private String id;
        }

        @NoArgsConstructor
        @Data
        public static class SigmaDTO {
            @JsonProperty("algorithm")
            private String algorithm;
            @JsonProperty("address")
            private String address;
            @JsonProperty("signature")
            private String signature;
            @JsonProperty("vin")
            private Integer vin;
            @JsonProperty("valid")
            private boolean valid;
        }
    }
}
