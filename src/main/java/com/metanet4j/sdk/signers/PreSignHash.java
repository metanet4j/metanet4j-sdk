package com.metanet4j.sdk.signers;

import com.metanet4j.sdk.context.PreSignHashContext;

public interface PreSignHash {
    PreSignHashContext getPreSignHashContext();

    boolean isHaveSign();
}
