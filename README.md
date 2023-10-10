# BAP、BitcoinSchema、1sat ordinals、sigma protocol support for java

## Requirement
java>=11
maven>=3.6

## Introduction

1.Integrate [BAP](https://github.com/icellan/bap)
with [bitcoinschema](https://bitcoinschema.org/#/)、[1sat ordinals](https://docs.1satordinals.com/libraries) 、[sigma protocol](https://docs.sigmaidentity.com/sigma-library)
to enable user transactions and identity association.
Ensure compatibility with the following protocols to achieve interoperability:
https://github.com/icellan/bap
https://github.com/icellan/bsocial
https://github.com/unwriter/b
https://github.com/rohenaz/map
https://github.com/attilaaf/AUTHOR_IDENTITY_PROTOCOL
https://github.com/bitcoinschema/js-1sat-ord
https://github.com/BitcoinSchema/sigma

2.Hiding the complexity of front-end development

You can achieve API services using the metanet4j-sdk. API callers only need to understand the API documentation, just
like regular application development. They don't need to be concerned with the specific implementation of the underlying
protocol. This is very helpful in simplifying development.

3.Remote signing for enhanced security with unmanaged keys

- [ ] Pending implementation of remote signing interface.
  Separating transaction construction from signing allows clients to perform the signing process, eliminating the need
  for service providers to manage user private keys. With remote signing, user private keys do not need to be
  transmitted over the network and remain securely stored locally. This provides convenience for applications that
  require higher security.

4.Server-side support for asynchronous tasks

By assembling transactions on the server-side, it becomes convenient to execute transactions asynchronously for tasks
such as uploading large files to the blockchain.

## Features

1. Support for ECIES shared key encryption and decryption.
   test case see: com.metanet4j.sdk.bap.BapBaseTest
   Compatible with the following JavaScript code:
   https://github.com/icellan/bap/blob/master/src/id.ts#L436
   https://github.com/icellan/bap/blob/master/src/id.ts#L456

2. Support for signing transactions with the private key corresponding to a BAP identity.

   code see :com.metanet4j.sdk.transcation.UnSpendableDataLockBuilder

3. BapDataLockBuilder.

   test case see:com.metanet4j.sdk.transcation.BitcoinschemaTransactionTest

    - [x] buildId

    - [x] buildAlias

    - [x] sign

4. BsocialDataLockBuilder.

   test case see: com.metanet4j.sdk.transcation.BitcoinschemaTransactionTest

    - [x] buildPost

    - [x] buildRepost

    - [x] buildReply

    - [x] buildLike

    - [x] buildUnLike

    - [x] buildFollow

    - [x] buildUnFollow

    - [x] buildTip
    - [ ] buildFriend
    - [ ] buildMessag
    - [ ] buildTags
    - [ ] buildAttachments

5. OrdScriptBuilder

    - [x] buildInscription

6. TransactionBuilder

   test case see: com.metanet4j.sdk.transcation.BitcoinschemaTransactionTest#buildTxThenBroadcast

7. OrdTransactionBuilder

   code see :

​ com.metanet4j.sdk.ordinals.OrdTransactionTemplate#createOrdinal

​ com.metanet4j.sdk.ordinals.OrdTransactionTemplate#sendOrdinal

8. parse tx to txo and bob

​ test case see :com.metanet4j.sdk.transcation.TransactionContextTest#parseTx(java.lang.String)

9. UTXOProvider FunctionalInterface

    - [x] com.metanet4j.sdk.utxo.impl.bitails.BitailsUtxoProvider

    - [ ] com.metanet4j.sdk.utxo.impl.gorillapool.OrdinalsOriginGorillaUtxoProvider

## Usage

Please review the test case.

## Building from source

git clone https://github.com/metanet4j/metanet4j-parent.git

git clone https://github.com/metanet4j/metanet4j-base.git

git clone https://github.com/metanet4j/metanet4j-sdk

mvn install

## examples

### origin

https://ordinals.gorillapool.io/api/inscriptions/origin/22d24e519b68330bd81458619eed7ed0e2bd3e69e6aa745faa99c93b9bedf0c4_0

```json
[
  {
    "txid": "22d24e519b68330bd81458619eed7ed0e2bd3e69e6aa745faa99c93b9bedf0c4",
    "vout": 0,
    "outpoint": "22d24e519b68330bd81458619eed7ed0e2bd3e69e6aa745faa99c93b9bedf0c4_0",
    "origin": "22d24e519b68330bd81458619eed7ed0e2bd3e69e6aa745faa99c93b9bedf0c4_0",
    "height": 812750,
    "idx": 1650,
    "lock": "c5cef11ea1e47715eff0bc6328d51183a6219f2747fa8edc51f5a8e2f75e0ec5",
    "SIGMA": [
      {
        "vin": 0,
        "valid": true,
        "address": "17HgWTSrWNNjxWs49FJ6utxsedxgxrt8Yb",
        "algorithm": "BSM",
        "signature": "G0xEyIvTFBHU+CQW/gy6uF0YyWDsgFI8Yntl5zevXQ66bH9ly2OqH88ZWaxPrc3XN8ick4nrmPUl2U4dXEOlNbo="
      }
    ],
    "listing": false,
    "bsv20": false,
    "num": 48015756,
    "id": 48015756,
    "file": {
      "hash": "5658e532fe8c11e034b504fcbe115b6d706b905c7722eac7b1b4258d0368f726",
      "size": 43,
      "type": "text/markdown"
    },
    "MAP": {
      "app": "metanet-sdk",
      "type": "ord"
    }
  }
]
```

### transfer

https://ordinals.gorillapool.io/api/utxos/origin/22d24e519b68330bd81458619eed7ed0e2bd3e69e6aa745faa99c93b9bedf0c4_0

```json
{
  "txid": "15a5e5faf2b89e10b6e0c7138c0d40e74177eb0d507a2f65656b35ed0e652b74",
  "vout": 0,
  "satoshis": 1,
  "lock": "c5cef11ea1e47715eff0bc6328d51183a6219f2747fa8edc51f5a8e2f75e0ec5",
  "script": "dqkU575zljkD4trd3dqsLSEgWZup6ruIrA==",
  "spend": "",
  "origin": "22d24e519b68330bd81458619eed7ed0e2bd3e69e6aa745faa99c93b9bedf0c4_0",
   "height": 812757,
   "idx": "1127",
   "listing": false
}
```

## links
https://bitcoinschema.org

https://www.twostack.org/

https://junglebus.gorillapool.io/docs/#/

https://scrypt.io/

https://bitcoinsv.io/

https://wiki.bitcoinsv.io/index.php/Main_Page


