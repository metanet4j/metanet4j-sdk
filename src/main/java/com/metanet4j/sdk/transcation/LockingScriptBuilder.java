

package com.metanet4j.sdk.transcation;


import com.metanet4j.sdk.remote.PreSignHash;
import com.metanet4j.bsv.script.Script;

public interface LockingScriptBuilder extends PreSignHash {
    Script getLockingScript();

}
