

package com.metanet4j.sdk.transcation;


import com.metanet4j.sdk.remote.PreSignHash;
import io.bitcoinsv.bitcoinjsv.script.Script;

public interface LockingScriptBuilder extends PreSignHash {
    Script getLockingScript();

}
