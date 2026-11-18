package com.metanet4j.sdk.transaction;

import com.metanet4j.bsv.script.Script;
import com.metanet4j.sdk.remote.PreSignHash;

public interface LockingScriptBuilder extends PreSignHash {
  Script getLockingScript();
}
