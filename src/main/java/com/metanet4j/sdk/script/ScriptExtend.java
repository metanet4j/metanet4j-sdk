package com.metanet4j.sdk.script;

import com.metanet4j.sdk.utils.TxHelperExtend;
import io.bitcoinsv.bitcoinjsv.script.Script;
import io.bitcoinsv.bitcoinjsv.script.ScriptBuilder;
import io.bitcoinsv.bitcoinjsv.script.ScriptParseException;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

public class ScriptExtend extends Script {

    public ScriptExtend(byte[] programBytes) throws ScriptParseException {
        super(programBytes);
    }

    public ScriptExtend(byte[] programBytes, long creationTimeSeconds) throws ScriptParseException {
        super(programBytes, creationTimeSeconds);
    }

    @Override
    public Script getScriptSigWithSignature(Script scriptSig, byte[] sigBytes, int index) {
        int sigsPrefixCount = 0;
        int sigsSuffixCount = 0;
        if (isPayToScriptHash()) {
            sigsPrefixCount = 1; // OP_0 <sig>* <redeemScript>
            sigsSuffixCount = 1;
        } else if (isSentToMultiSig()) {
            sigsPrefixCount = 1; // OP_0 <sig>*
        } else if (isSentToAddress()) {
            sigsSuffixCount = 1; // <sig> <pubkey>
        } else if (TxHelperExtend.isSentToOrdinals(this)) {
            sigsSuffixCount = 1; // <sig> <pubkey>
        }
        return ScriptBuilder.updateScriptWithSignature(scriptSig, sigBytes, index, sigsPrefixCount, sigsSuffixCount);
    }

    @Override
    public int getNumberOfBytesRequiredToSpend(@Nullable int pubKeyLen, @Nullable Script redeemScript) {
        if (isPayToScriptHash()) {
            // scriptSig: <sig> [sig] [sig...] <redeemscript>
            checkArgument(redeemScript != null, "P2SH script requires redeemScript to be spent");
            return redeemScript.getNumberOfSignaturesRequiredToSpend() * SIG_SIZE + redeemScript.getProgram().length;
        } else if (isSentToMultiSig()) {
            // scriptSig: OP_0 <sig> [sig] [sig...]
            return getNumberOfSignaturesRequiredToSpend() * SIG_SIZE + 1;
        } else if (isSentToRawPubKey()) {
            // scriptSig: <sig>
            return SIG_SIZE;
        } else if (isSentToAddress()) {
            // scriptSig: <sig> <pubkey>
            int uncompressedPubKeySize = 65;
            return SIG_SIZE + (pubKeyLen > 0 ? pubKeyLen : uncompressedPubKeySize);
        } else if (TxHelperExtend.isSentToOrdinals(this)) {
            // scriptSig: <sig> <pubkey>
            int uncompressedPubKeySize = 65;
            return SIG_SIZE + (pubKeyLen > 0 ? pubKeyLen : uncompressedPubKeySize);
        } else {
            throw new IllegalStateException("Unsupported script type");
        }
    }

}
