package com.metanet4j.sdk.sigma;


import io.bitcoinsv.bitcoinjsv.core.Address;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.script.Script;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SignResponse extends Sig {

  Script sigmaScript;
  Transaction signedTx;

  public SignResponse(String address, String signature, String algorithm, int vin, int targetVout, Script sigmaScript, Transaction signedTx) {
    super(address, signature, algorithm, vin, targetVout);
    this.sigmaScript = sigmaScript;
    this.signedTx = signedTx;
  }

}