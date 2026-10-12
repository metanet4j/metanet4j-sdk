package com.metanet4j.sdk;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metanet4j.bsv.script.LockingScript;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.script.Spend;
import com.metanet4j.bsv.script.UnlockingScript;
import com.metanet4j.bsv.transaction.Transaction;
import com.metanet4j.bsv.transaction.TransactionInput;
import com.metanet4j.bsv.transaction.TransactionOutput;
import com.metanet4j.sdk.utils.ScriptHelper;
import com.metanet4j.sdk.utils.TxHelperExtend;
import java.util.ArrayList;

/** 交易合同测试的真实脚本执行断言；签名存在不等于签名有效。 */
public final class TransactionScriptAssertions {
    private TransactionScriptAssertions() {
    }

    public static void assertInputScriptValid(Transaction tx, int index) {
        TransactionInput input = tx.inputs.get(index);
        TransactionOutput source = TxHelperExtend.sourceOutputOf(input);
        assertNotNull(source, "输入必须有对应索引的连接输出");
        Script script = input.unlockingScript instanceof Script value ? value
                : Script.fromBinary((byte[]) input.unlockingScript);
        Spend.Params params = new Spend.Params();
        params.sourceTXID = String.valueOf(input.sourceTXID);
        params.sourceOutputIndex = input.sourceOutputIndex;
        params.sourceSatoshis = ((Number) source.satoshis).doubleValue();
        params.lockingScript = new LockingScript(ScriptHelper.chunksOf(source.lockingScript));
        params.unlockingScript = new UnlockingScript(ScriptHelper.chunksOf(script));
        params.transactionVersion = Math.toIntExact(tx.version);
        params.allInputs = tx.inputs;
        params.otherInputs = new ArrayList<>(tx.inputs);
        params.otherInputs.remove(index);
        params.outputs = tx.outputs;
        params.inputIndex = index;
        params.inputSequence = input.sequence;
        params.lockTime = tx.lockTime;
        assertTrue(new Spend(params).validate(), "签名必须通过实际锁定／解锁脚本验证");
    }
}
