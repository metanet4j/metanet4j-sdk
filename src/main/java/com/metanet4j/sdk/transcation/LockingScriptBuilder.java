

package com.metanet4j.sdk.transcation;


import com.metanet4j.sdk.signers.PreSignHash;
import io.bitcoinsv.bitcoinjsv.script.Script;

public interface LockingScriptBuilder extends PreSignHash {
    Script getLockingScript();

}
