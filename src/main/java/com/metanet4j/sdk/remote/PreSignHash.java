package com.metanet4j.sdk.remote;

import com.metanet4j.sdk.context.PreSignHashContext;

public interface PreSignHash {
    PreSignHashContext getPreSignHashContext();

    boolean isHaveSign();
}
