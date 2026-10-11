package com.metanet4j.sdk.exception;

public class SignErrorException extends RuntimeException {
    public SignErrorException() {
        super();
    }

    /** 带原因的构造器：便于把提供者抛出的原始异常保留为 cause，不丢失现场。 */
    public SignErrorException(String message, Throwable cause) {
        super(message, cause);
    }

    public SignErrorException(String message) {
        super(message);
    }
}
