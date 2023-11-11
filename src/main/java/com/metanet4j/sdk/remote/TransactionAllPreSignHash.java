package com.metanet4j.sdk.remote;

import com.metanet4j.sdk.bap.BapBaseCore;
import com.metanet4j.sdk.context.PreSignHashContext;

import java.util.List;

public interface TransactionAllPreSignHash {
    List<PreSignHashContext> getAllBsmSignHash(boolean useForkId, boolean anyoneCanPay, BapBaseCore baseCore);
}
