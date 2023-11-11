package com.metanet4j.sdk.remote;

import com.google.common.collect.Lists;
import com.metanet4j.sdk.context.PreSignHashContext;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;

import java.util.List;

public final class PreSignHashUtils {

    private static final ThreadLocal<List<PreSignHashContext>> STORE = new ThreadLocal<List<PreSignHashContext>>() {
        protected List<PreSignHashContext> initialValue() {
            return Lists.newArrayList();
        }
    };

    public static void setPreSignHashContext(PreSignHashContext preSignHashContext) {
        STORE.get().add(preSignHashContext);
    }

    public static PreSignHashContext getSignedHashContext(Sha256Hash hash) {
        PreSignHashContext preSignHashContext = STORE.get().stream().filter(o -> o.getPreSignHash().equals(hash)).findFirst().orElse(null);
        if (preSignHashContext == null) {
            throw new RuntimeException("get preSignHashContext is null,the hash is " + hash.toString());
        }
        return preSignHashContext;
    }

    public static void clear() {
        STORE.get().clear();
        STORE.remove();

    }
}
