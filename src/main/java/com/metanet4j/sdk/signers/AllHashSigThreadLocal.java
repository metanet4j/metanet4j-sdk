package com.metanet4j.sdk.signers;

import com.google.common.collect.Lists;
import com.metanet4j.sdk.context.PreSignHashContext;
import com.metanet4j.base.bsv.BsvHashBytes;

import java.util.List;

@Deprecated
public class AllHashSigThreadLocal extends ThreadLocal<List<PreSignHashContext>> {
    public AllHashSigThreadLocal() {
    }

    @Override
    public List<PreSignHashContext> initialValue() {
        return Lists.newArrayList();
    }


    public PreSignHashContext getSignedHashContext(BsvHashBytes hash) {
        PreSignHashContext preSignHashContext = this.get().stream().filter(o -> o.getPreSignHash().equals(hash)).findFirst().orElse(null);
        if (preSignHashContext == null) {
            throw new RuntimeException("get preSignHashContext is null,the hash is " + hash.toString());
        }
        return preSignHashContext;
    }


}
