# Bitcoin Attestation Protocol - BAP
> A simple protocol to create a chain of trust for any kind of data on the Bitcoin blockchain
Authors: Siggi

Special thanks to Attila Aros & Satchmo

Inspired by the [AUTHOR IDENTITY Protocol](https://github.com/BitcoinFiles/AUTHOR_IDENTITY_PROTOCOL)

NOTE: All examples in this document use fake identity keys, addresses and signatures. See the test data sets for real world examples that can be used in a test suite when developing a library.

- [Intro](#intro)
- [Protocol](#protocol)
  * [URN - Uniform Resource Names](#urn---uniform-resource-names)
  * [Attributes are defined at schema.org](#attributes-are-defined-at-schemaorg)
- [Creating an identity (ID)](#creating-an-identity-id)
- [Usage in an identity system (BAP-ID)](#usage-in-an-identity-system-bap-id)
  * [Attesting (ATTEST)](#attesting-attest)
  * [Verifying an identity attribute](#verifying-an-identity-attribute)
  * [Delegating signing to another identity](#delegating-signing-to-another-identity)
  * [Publishing Identity information (ALIAS)](#publishing-identity-information-alias)
  * [BAP uniKey](#bap-unikey)
- [Publishing data (DATA)](#publishing-data)
- [Using as a Power of Attorney](#using-as-a-power-of-attorney)
- [Blacklisting](#blacklisting)
  * [Blacklisting transactions / addresses](#blacklisting-transactions--addresses)
  * [Blacklisting IP addresses](#blacklisting-ip-addresses)
  * [Final note on blacklisting](#final-note-on-blacklisting)
- [Giving consent to access of data](#giving-consent-to-access-of-data)
- [Simple asserts](#simple-asserts)
- [Revoking an attestation](#revoking-an-attestation)
- [BAP on the BSV Metanet - PROVISIONAL](#bap-on-the-bsv-metanet---provisional)
- [BAP w3c DID - PROVISIONAL](#bap-w3c-did---provisional)
- [Extending the protocol](#extending-the-protocol)

# TODO
- Finish DID specs - help needed
- Request feedback

# Intro

The design goals:

1. A simple protocol for generic attestation of data, without the need to publish the data itself
2. Decouple the signing with an address from the funding source address (ie: does not require any on-chain transactions from the signing identity address)
3. Allow for rotation of signing keys without having to change the existing attestations
4. Allow for creation of an infinite amount of identities, but still allow for proving of attested attributes between the identities

# Protocol

The protocol is defined using the [Bitcom](https://bitcom.bitdb.network/) convention. The signing is done using the [AUTHOR IDENTITY Protocol](https://github.com/BitcoinFiles/AUTHOR_IDENTITY_PROTOCOL).

- The prefix of the protocol is `1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMT`;

```
1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMT
[ID|ATTEST|ALIAS|DATA|REVOKE]
[ID Key|URN Attestation Hash]
[Sequence|Address|Data]
|
[AIP protocol address]
[AIP Signing Algorithm]
[AIP Signing Address]
[AIP Signature]
```
By default, all fields are signed, so the optional indices of the AIP can be left out.

The `Sequence` is added to the transaction to prevent replay of the transaction in case of a revocation. The transaction from the same signatory, with the highest `Sequence` is the current one.

The fourth field is used for the bitcoin signing address in an `ID` transaction only.

Example:

```
1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMT
ATTEST
d4bcdd0f437d0d3bc588bb4e861d2e83e26e8bf9566ae541a5d43329213b1b13
0
|
15PciHG22SNLQJXMoSUaWVi7WSqc7hCfva
BITCOIN_ECDSA
1Po6MLAPJsSAGyE8sXw3CgWcgxumNGjyqm
G8wW0NOCPFgGSoCtB2DZ+0CrLrh9ywAl0C5hnx78Q+7QNyPYzsFtkt4/TXkzkTwqbOT3Ofb1CYdNUv5a/jviPVA=
```

## URN - Uniform Resource Names
The protocol makes use of URN's as a data carrier for the attestation data, as defined by the [w3c](https://www.w3.org/TR/uri-clarification/).

URN's look like:

```
urn:[namespace identifier]:[...URN]
```

Examples for use in BAP:

Identity:
```
urn:bap:id:[Attribute name]:[Attribute value]:[Nonce]
  urn:bap:id:name:John Doe:e2c6fb4063cc04af58935737eaffc938011dff546d47b7fbb18ed346f8c4d4fa
```

Attestations:
```
urn:bap:attest:[Attribute hash]:[Identity key]
urn:bap:attest:42d2396ddfc3dec6acbd96830b844a10b8b2f065e60fbd5238b5267ab086bf4f:1CCWY6EXZwNqbrtW1SXGNFWdwipYT7Ur1Q
```

The URN is hashed using sha256 when used in a transaction sent to the blockchain.

## Attributes are defined at schema.org

Attributes used in BAP should be defined at https://schema.org.

Especially the Person attributes, found at https://schema.org/Person, should be used in BAP.

# Creating an identity (ID)

To create a new identity in BAP, we need to create 2 private keys and compute they public keys and the corresponding addresses. For easy management of the keys, it is recommended to use an [HD Private key](https://docs.moneybutton.com/docs/bsv-hd-private-key.html) with known derivations.

```
rootAddress: 1WffojxvgpQBmUTigoss7VUdfN45JiiRK
firstAddress: 1K4c6YXR1ixNLAqrL8nx5HUQAPKbACTwDo
```



Signing identities in BAP are created by linking a unique identity key with bitcoin signing addresses. The identity key should be computed as a hash of the rootAddress. This links the 2 together and prevents others from creating an identity using the same identity key to create confusion.

The identity key follows the way a Bitcoin address is hashed to reduce the size of the key.

```
identityKey = base58( ripemd160 ( sha256 ( rootAddress ) ) )
```

Example identity key, note the hex values are fed as binary buffers to the hash functions and not as a string:

```
sha256(1WffojxvgpQBmUTigoss7VUdfN45JiiRK) = c38bc59316de9783b5f7a8ba19bc5d442f6c9b0988c48a241d1c58a1f4e9ae19
ripemd160(c38bc59316de9783b5f7a8ba19bc5d442f6c9b0988c48a241d1c58a1f4e9ae19) = afb3dcf52c2c661c35c8ec6a92cecbfc691ba371
base58(afb3dcf52c2c661c35c8ec6a92cecbfc691ba371) = 3SyWUZXvhidNcEHbAC3HkBnKoD2Q
identityKey: 3SyWUZXvhidNcEHbAC3HkBnKoD2Q
```

**NOTE:** This has been changed with the release of the BAP library. This allows a verifier to link the root address to the identity key. In the past the identity key was random. Older identites created at random will still work with the BAP library, but new identities should be created in this way.

To link this identity key to the root address and the signing address, an `ID` transaction is sent to the blockchain:

```
1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMT
ID
3SyWUZXvhidNcEHbAC3HkBnKoD2Q
1K4c6YXR1ixNLAqrL8nx5HUQAPKbACTwDo
|
15PciHG22SNLQJXMoSUaWVi7WSqc7hCfva
BITCOIN_ECDSA
1WffojxvgpQBmUTigoss7VUdfN45JiiRK
HB6Ye7ekxjKDkblJYL9lX3J2vhY75vl+WfVCq+wW3+y6S7XECkgYwUEVH3WEArRuDb/aVZ8ntLI/D0Yolb1dhD8=
```

The address `1WffojxvgpQBmUTigoss7VUdfN45JiiRK` associated with the first instance of the identity key on-chain, is the identity control address (or rootAddress). This address should no be used anywhere, but can be used to destroy the identity, in case the latest linked key has been compromised.

When the signing address is rotated to a new key, a new ID transaction is created, this time signed by the previous address:

```
1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMT
ID
3SyWUZXvhidNcEHbAC3HkBnKoD2Q
1JfMQDtBKYi6z65M9uF2gxgLv7E8pPR6MA
|
15PciHG22SNLQJXMoSUaWVi7WSqc7hCfva
BITCOIN_ECDSA
1K4c6YXR1ixNLAqrL8nx5HUQAPKbACTwDo
HB6Ye7ekxjKDkblJYL9lX3J2vhY75vl+WfVCq+wW3+y6S7XECkgYwUEVH3WEArRuDb/aVZ8ntLI/D0Yolb1dhD8=
```

In this way, we have created a way to rotate the signing keys for a certain identity as often as we want, with each signing key being immutably saved on the blockchain.

Any signatures done for the identity key should be done using the active key at that time.

To destroy the identity, an ID transaction is sent to 0, signed with the address from the first ever transaction `1WffojxvgpQBmUTigoss7VUdfN45JiiRK`;

```
1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMT
ID
3SyWUZXvhidNcEHbAC3HkBnKoD2Q
0
|
15PciHG22SNLQJXMoSUaWVi7WSqc7hCfva
BITCOIN_ECDSA
1WffojxvgpQBmUTigoss7VUdfN45JiiRK
HB6Ye7ekxjKDkblJYL9lX3J2vhY75vl+WfVCq+wW3+y6S7XECkgYwUEVH3WEArRuDb/aVZ8ntLI/D0Yolb1dhD8=
```

# Usage in an identity system (BAP-ID)

A BAP identity is defined as an identity key that has attested identity attributes, verified by one or more authorities. These authorities are outside the scope of this description, but are not governed or controlled.

All identity attributes have the following characteristics:

```
urn:bap:id:[Attribute name]:[Attribute value]:[Nonce]
```

Attribute | Description
--------- | ----------
Attribute name | The name of the attribute being described
Attribute value | The value of the attribute being described with the name
Nonce | A unique random string to make sure the entropy of hashing the urn will not cause collision and not allow for dictionary attacks

A user may want to create multiple identities with a varying degree of details available about that identity. Let's take a couple of examples:

Identity 1 (`3SyWUZXvhidNcEHbAC3HkBnKoD2Q`):
```
urn:bap:id:name:John Doe:e2c6fb4063cc04af58935737eaffc938011dff546d47b7fbb18ed346f8c4d4fa
urn:bap:id:birthday:1990-05-22:e61f23cbbb2284842d77965e2b0e32f0ca890b1894ca4ce652831347ee3596d9
urn:bap:id:over18:1:480ca17ccaacd671b28dc811332525f2f2cd594d8e8e7825de515ce5d52d30e8
urn:bap:id:address:51391 Moorpark Ave #104, San Jose, CA 95129, United States:44d47d2375c8346c7ceeab1904360aaf572b1c940c1bd66ffd5cf88fdf06bc05
urn:bap:id:passportNr:US2343242:9c06a0fb0e2d9cef4928855076255e4df3375e2807cf37bc028ddb282f811ac8
urn:bap:id:passportExpiration:2022-02-23:d61a39afb463b42c3e419463a028deb3e9e2cebf67953864e9f9e7869677e7cb
```

Identity 2 (`b71a658ec49a9cb099fd5d3cf0aafce28f1d464fa6e496f61c8048d8ed56edc1`):
```
urn:bap:id:name:John Doe:6637be9df2e114ce19a287ff48841899ef4a5762a5f9dc47aef62fe4f579bf93
urn:bap:id:email:john.doen@example.com:2864fd138ab1e9ddaaea763c77a45898dac64a26229f9f3d0f2280e4bfa915de
urn:bap:id:over18:1:5f48f9be1644834933cec74a299d109d18f01e77c9552545d2eae4d0c929000b
```

Identity 3 (`10ef2b1bb05185d0dbae41e1bfefe0c2deb2d389f38fe56daa2cc28a9ba82fc7`):
```
urn:bap:id:alternateName:Johnny:7a8d693bce6b6c1cf1dd81468a52b69829e465ff9b0762cf77965309df3ad4c8
```

NOTE: The random nonce should not be re-used across identities. Always create a new random secret for each attribute.

## Attesting (ATTEST)

Anyone can attest for any identity by broadcasting a bitcoin transaction with a signature from their private key of the attributes of the identity.

All attestations have the following characteristics:

```
urn:bap:attest:[Attribute hash]:[Identity key]
```

Attribute | Description
--------- | ----------
Attribute hash | A hash of the urn attribute being attested
Identity key | The unique identity key of the owner of the attestation

Take for example a bank, Banco De Bitcoin, with a known and trusted identity key of `ezY2h8B5sj7SHGw8i1KhHtRvgM5` which is linked via an `ID` transaction to `1K4c6YXR1ixNLAqrL8nx5HUQAPKbACTwDo`. To attest that the bank has seen the information in the identity attribute and that it is correct, the bank would sign an attestation with the identity information together with the given identity key.

```
1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMT
ATTEST
[Attestation hash]
[Sequence]
|
15PciHG22SNLQJXMoSUaWVi7WSqc7hCfva
[Signature algorithm]
[Address of signer]
[Signature]
```

For the name urn for the Identity 1 (`3SyWUZXvhidNcEHbAC3HkBnKoD2Q`) in above example:

- We take the hash of `urn:bap:id:name:John Doe:e2c6fb4063cc04af58935737eaffc938011dff546d47b7fbb18ed346f8c4d4fa` = `b17c8e606afcf0d8dca65bdf8f33d275239438116557980203c82b0fae259838`
- Then create an attestation urn for the address: `urn:bap:id:attest:b17c8e606afcf0d8dca65bdf8f33d275239438116557980203c82b0fae259838:3SyWUZXvhidNcEHbAC3HkBnKoD2Q`
- Then hash the attestation for our transaction: `89cd658c0ce3ff62db4270a317c35f8a7dfe1242e2cc94232aa3947d77f82431`
- Then the attestation is signed with the private key belonging to the trusted authority (with address `1K4c6YXR1ixNLAqrL8nx5HUQAPKbACTwDo`);

```
1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMT
ATTEST
89cd658c0ce3ff62db4270a317c35f8a7dfe1242e2cc94232aa3947d77f82431
0
|
15PciHG22SNLQJXMoSUaWVi7WSqc7hCfva
BITCOIN_ECDSA
1K4c6YXR1ixNLAqrL8nx5HUQAPKbACTwDo
HB6Ye7ekxjKDkblJYL9lX3J2vhY75vl+WfVCq+wW3+y6S7XECkgYwUEVH3WEArRuDb/aVZ8ntLI/D0Yolb1dhD8=
```

Since the hash of our attestation is always the same, any authority attesting the identity attribute will broadcast a transaction where the 3rd item is the same. In this way it is possible to search (using for instance Planaria) through the blockchain for all attestations of the identity attribute and select the one most trusted.

## Verifying an identity attribute

For a user to prove their identity, that has been verified by a trusted authority, the user does the following.

He shares his identity key `3SyWUZXvhidNcEHbAC3HkBnKoD2Q`, the full urn `urn:bap:id:name:John Doe:e2c6fb4063cc04af58935737eaffc938011dff546d47b7fbb18ed346f8c4d4fa` and signs a challenge message from the party that request an identity verification.

The receiving party can now verify:
- That the user is the owner of the address `1JfMQDtBKYi6z65M9uF2gxgLv7E8pPR6MA` by verifying the signature
- That the identity `3SyWUZXvhidNcEHbAC3HkBnKoD2Q` is linked to the address via an `ID` record
- That the attestation urn has been signed by that latest valid address of Banco De Bitcoin.
- Thereby verifying that the user signing the message has been attested by the bank to have the name `John Doe`.

NOTE: No unneeded sensitive information has been shared and it is not possible to infer any other information from the information sent. The only thing the receiver now knows is that the person doing the signing is called John Doe.

## Delegating signing to another identity

With BAP, it is very easy to create an infinite number of identities for a user. This is even encouraged to preserve privacy.

When for instance a KYC check is done, this check is done for a certain identity. Replicating this KYC check for all identities for a user, to be able to use in all access applications, is impractical.  To solve this we must be able to link, or delegate, from one identity to another.

`urn:bap:delegate:<from idKey>:<to IdKey>:<Nonce>`

Example:
```
var attestation = 'urn:bap:delegate:3SyWUZXvhidNcEHbAC3HkBnKoD2Q:341d782c56a588ccdd3ebb181d1dbb4699bdb5fb9956b7bd07e917d955acdb04:7f2fe5aac07e2d4c43bdb232029ed157acf0272eac94a2f75cc17566c01a5e89';
var attestationHash = sha256(attestation); // 2dbb381888f973a0db3bf311e551a6ac2f3ab792420262d8a6f65ef4feb8c1ef
```

This links, or delegates, from identity `3SyWUZXvhidNcEHbAC3HkBnKoD2Q` to identity `341d782c56a588ccdd3ebb181d1dbb4699bdb5fb9956b7bd07e917d955acdb04`;

The attestation can be published to the blockchain, signed by the identity `3SyWUZXvhidNcEHbAC3HkBnKoD2Q`;

```
1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMT
ATTEST
2dbb381888f973a0db3bf311e551a6ac2f3ab792420262d8a6f65ef4feb8c1ef
0
|
15PciHG22SNLQJXMoSUaWVi7WSqc7hCfva
BITCOIN_ECDSA
1JfMQDtBKYi6z65M9uF2gxgLv7E8pPR6MA
HB6Ye7ekxjKDkblJYL9lX3J2vhY75vl+WfVCq+wW3+y6S7XECkgYwUEVH3WEArRuDb/aVZ8ntLI/D0Yolb1dhD8=
```

Showing that it is possible to use the verified attributes from `3SyWUZXvhidNcEHbAC3HkBnKoD2Q` in identity `341d782c56a588ccdd3ebb181d1dbb4699bdb5fb9956b7bd07e917d955acdb04` can now be done by sharing the delegation. The delegation attestation will be available to check on-chain.

The challenge sent by the application requesting the attributes should be signed by **both (!)** identites, to proof access to the private keys of both identities.

NOTE: The grant published on-chain for the attributes shared must be signed by the delegating identity `3SyWUZXvhidNcEHbAC3HkBnKoD2Q`, and not `341d782c56a588ccdd3ebb181d1dbb4699bdb5fb9956b7bd07e917d955acdb04`;

NOTE: The receiving end should store both identity keys to be able to proof they have received access to the data of the user. The could be stored as a concatenation of the two identity strings: `341d782c56a588ccdd3ebb181d1dbb4699bdb5fb9956b7bd07e917d955acdb04<-3SyWUZXvhidNcEHbAC3HkBnKoD2Q`;

NOTE: The main identity, for which a KYC has been done, should never directly be used in any application. A new identity should be created for each and every application accessed.

## Publishing Identity information (ALIAS)

It is possible to publish information about an identity by using the BAP ALIAS keyword. This tells the world that an identity key should be seen as belonging to the entity published in the alias.

It is recommended to only use the ALIAS keyword for companies that need (or want) to link an identity key to their business. It is not recommended for normal users to publish their identity using the ALIAS keyword.

The ALIAS data is a stringified JSON object that uses the w3c attributes from https://schema.org/. Example:
```
1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMT
ALIAS
3SyWUZXvhidNcEHbAC3HkBnKoD2Q
{"@type":"Organization","name":"Banco De Bitcoin","address":{"@type":"PostalAddress","addressLocality":"Mexico Beach","addressRegion":"FL","streetAddress":"3102 Highway 98"},"url":https://bancodebitcoin.com","logo":{"@type":"ImageObject","contentUrl":"data:image/png;base64,..."}
|
15PciHG22SNLQJXMoSUaWVi7WSqc7hCfva
BITCOIN_ECDSA
1K4c6YXR1ixNLAqrL8nx5HUQAPKbACTwDo
HB6Ye7ekxjKDkblJYL9lX3J2vhY75vl+WfVCq+wW3+y6S7XECkgYwUEVH3WEArRuDb/aVZ8ntLI/D0Yolb1dhD8=
```

Organizations are encouraged to use attributes defined at https://schema.org/Organization when using the ALIAS keyword.

## BAP uniKey

The BAP uniKey is a unique hash of identity attributes of a person to create a uniquely identifiable hash to use across services. The uniKey can also be used in a custodial identity service where the only thing shared with a service is the uniKey and the uniKey expiration. The real identity can be looked up at the custodial service.

The BAP uniKey is defined as follows:
```
privateUniKey := sha256(
  fullName
  nationality
  birthDate
  socialSecurityNr
  passportNr
  passportExpirationDate
  base64(passport image)
)
uniKey := sha256(privateUniKey)
```
The privateUniKey should only be defined based on a digital (NFC) passport that supports all the attributes mentioned. Adding the base64 data of the image on the passport is especially important to create enough entropy for the hashing function to safe guard against dictionary attacks. The uniKey is a hash of the privateUniKey.

The privateUniKey should __never__ be shared. The uniKey should only be shared in situations where the user would normally share all his personal info (KYC check).

The `uniKeyExpirationDate` is equal to the `passportExpirationDate`.

Example:
```
privateUniKey = "27dacfd5d0eefd927263a502bd2dea7b9b6193ba7f8bf98c2f4855b45e7d0008";
uniKey = "dce3ba1199b74d74a5fb941f92ed7e6adb97acc4dffdb055cf6867162dc8fa74";
urn:bap:id:uniKey:dce3ba1199b74d74a5fb941f92ed7e6adb97acc4dffdb055cf6867162dc8fa74:c490de4c40fecd9fa7c3979b9134d0a419fa77388d1535b48a24cdb69425b5d5
hash = "bc47cdfc44cc89519dca5f12bc10c53492c57090a012d2540257bf02772f82a5";
```
`c490de4c40...` is just a random nonce to increase entropy.
```
uniKeyExpirationDate = "2025-04-20";
urn:bap:id:uniKeyExpirationDate:2025-04-20:dce3ba1199b74d74a5fb941f92ed7e6adb97acc4dffdb055cf6867162dc8fa74
hash = "a67e363c4163b49f81529b8b5118670ffbf6d76e2b47a03a01e6e21805388f7f";
```
The urn of the uniKeyExpirationDate should use the uniKey as the nonce. This links them together and makes it verifiable for third parties.

# Publishing data

The BAP protocol defines a way to publish data on-chain, optionally in a way that only the sender and receiver can read the data.

The data can be encrypted using the ECIES encryption scheme (specifically electrum-ecies, https://www.npmjs.com/package/electrum-ecies).

```
1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMT
DATA
[Attestation hash]
[[Optionally ECIES encrypted] data]
|
15PciHG22SNLQJXMoSUaWVi7WSqc7hCfva
[Signature algorithm]
[Address of signer]
[Signature]
```

Example:
```
1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMT
DATA
2dbb381888f973a0db3bf311e551a6ac2f3ab792420262d8a6f65ef4feb8c1ef
QklFMQMFmPdvjFe8Wfo+JWmTpo+33LXc+4G8ThfaucU72kieb6lWEv4layTb0x5tzpi6lA2it8rO/ELrXomJqC53uBOd+DZSzDhCSpK6SwR+Itt+Pw==
|
15PciHG22SNLQJXMoSUaWVi7WSqc7hCfva
BITCOIN_ECDSA
1WffojxvgpQBmUTigoss7VUdfN45JiiRK
HB6Ye7ekxjKDkblJYL9lX3J2vhY75vl+WfVCq+wW3+y6S7XECkgYwUEVH3WEArRuDb/aVZ8ntLI/D0Yolb1dhD8=
```
When adding data like this to an attestation, the identity of the signing party should match the identity of the attestation, otherwise the data should be ignored.

Or as a part of an attestation transaction:
```
1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMT
ATTEST
5e991865273588e8be0b834b013b7b3b7e4ff2c7517c9fcdf77da84502cebef1
0
|
1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMT
DATA
5e991865273588e8be0b834b013b7b3b7e4ff2c7517c9fcdf77da84502cebef1
QklFMQMFmPdvjFe8Wfo+JWmTpo+33LXc+4G8ThfaucU72kieb6lWEv4layTb0x5tzpi6lA2it8rO/ELrXomJqC53uBOd+DZSzDhCSpK6SwR+Itt+Pw==
|
15PciHG22SNLQJXMoSUaWVi7WSqc7hCfva
BITCOIN_ECDSA
1K4c6YXR1ixNLAqrL8nx5HUQAPKbACTwDo
HB6Ye7ekxjKDkblJYL9lX3J2vhY75vl+WfVCq+wW3+y6S7XECkgYwUEVH3WEArRuDb/aVZ8ntLI/D0Yolb1dhD8=
```

This can be used to append data to an attestation, for instance to add context to the attestation for a UI of an application.

For maximum compatibility with ECIES, the data should be encrypted using the private key of the attester and the public key of the owner of the identity for which the attestation is being done (Traditional 2 keys ECIES, https://www.npmjs.com/package/electrum-ecies#traditional-2-keys-ecies).

# Using as a Power of Attorney

All users that have an identity and an address registered, should be able to, for instance, give another user temporary rights to sign on their behalf. A Power of Attorney could be defined with the BAP protocol in the following way.
All users that have an identity and an address registered, should be able to, for instance, give another user temporary rights to sign on their behalf. A Power of Attorney could be defined with the BAP protocol in the following way.

(A power of attorney of this kind is only valid in the real world, but does not allow anyone else to sign anything on-chain)

The Power of Attorney would have the following characteristics:

```
urn:bap:poa:[PoA Attribute]:[Address]:[Nonce]
```
Attribute | Description
--------- | ----------
PoA Attribute | Power of Attorney attribute being handed over to the person with the identity associates with Address
Address | The bitcoin address of the person (or organisation) being handed the PoA
Nonce | A unique random string to make sure the entropy of hashing the urn will not cause collision and not allow for dictionary attacks

PoA attributes:

Attribute | Description
--------- | ----------
real-estate | To buy, sell, rent, or otherwise manage residential, commercial, and personal real estate
business | To invest, trade, and manage any and all business transactions and decisions, as well as handle any claim or litigation matters
finance | To control banking, tax, and government and retirement transactions, as well as living trust and estate decisions
family | To purchase gifts, employ professionals, and to buy, sell or trade any of your personal property
general | This grants the authority to make any decisions that you would be able to if you were personally present

Example, give the bank the Power of Attorney over finances:

For the Identity 1 (`3SyWUZXvhidNcEHbAC3HkBnKoD2Q`) given PoA to the bank `ezY2h8B5sj7SHGw8i1KhHtRvgM5`:

- We take the hash of `urn:bap:poa:finance:3SyWUZXvhidNcEHbAC3HkBnKoD2Q:ef4ef3b8847cf9533cc044dc032269f80ecf6fcbefbd4d6ac81dddc0124f50e7`
- Then hash the poa for the transaction: `77cdec21e1025f85a5cb3744d5515c54783c739b8fa7c72c9e24d83900261d7f`
- Then the poa is signed with the private key belonging to the identity handing over the PoA (with address `1JfMQDtBKYi6z65M9uF2gxgLv7E8pPR6MA`);

```
1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMT
ATTEST
77cdec21e1025f85a5cb3744d5515c54783c739b8fa7c72c9e24d83900261d7f
0
|
15PciHG22SNLQJXMoSUaWVi7WSqc7hCfva
BITCOIN_ECDSA
1JfMQDtBKYi6z65M9uF2gxgLv7E8pPR6MA
HB6Ye7ekxjKDkblJYL9lX3J2vhY75vl+WfVCq+wW3+y6S7XECkgYwUEVH3WEArRuDb/aVZ8ntLI/D0Yolb1dhD8=
```

The bank will save the urn and can prove that the PoA is still valid on the blockchain.

The user can always revoke the PoA with a REVOKE transaction.

# Blacklisting

With more and more data being posted to the bitcoin blockchain it becomes ever more important to be able to block data from being viewed on sites offering that service. Especially illegal data needs to be filtered from viewing to prevent liability claims.

A proposed format for blacklisting any type of data could have the following format:
```
urn:bap:blacklist:[type]:[attribute]:[key]
```

## Blacklisting transactions / addresses

Using the blacklisting format, a transaction ID blacklist would be of the following format for a transaction ID:
```
urn:bap:blacklist:bitcoin:tx-id:[Transaction ID]
```

Example, blacklisting transaction ID `9e4b52ca8abe317d246ae2e742898df0956eaf1cc8df7c02154d20c1f55f3f9b`:
```
urn:bap:blacklist:bitcoin:tx-id:9e4b52ca8abe317d246ae2e742898df0956eaf1cc8df7c02154d20c1f55f3f9b
```

The hash of this blacklisting is: `8a6bc20369171516fb9155a10f11caff8a51dbd8ae90c5bf3443fc4c83bdc8e8`

The attestation looks like:
```
1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMT
ATTEST
8a6bc20369171516fb9155a10f11caff8a51dbd8ae90c5bf3443fc4c83bdc8e8
0
|
15PciHG22SNLQJXMoSUaWVi7WSqc7hCfva
BITCOIN_ECDSA
1JfMQDtBKYi6z65M9uF2gxgLv7E8pPR6MA
HB6Ye7ekxjKDkblJYL9lX3J2vhY75vl+WfVCq+wW3+y6S7XECkgYwUEVH3WEArRuDb/aVZ8ntLI/D0Yolb1dhD8=
```

Which would indicate that the ID signing with `1JfMQDtBKYi6z65M9uF2gxgLv7E8pPR6MA` is blacklisting the transaction. This way services can blacklist transactions and publish that on-chain for other services to see.

Third party services could be using this to check whether services they trust are blacklisting transactions and based on that decide not to show them in their viewer. A Simple query of the attestastion hash `8a6bc20369171516fb9155a10f11caff8a51dbd8ae90c5bf3443fc4c83bdc8e8` in a BAP index would return all the services that have blacklisted the transaction.

For a bitcoin address, the blacklist urn would look like:
```
urn:bap:blacklist:bitcoin:address:[Address]
```
Example:
```
urn:bap:blacklist:bitcoin:address:1JfMQDtBKYi6z65M9uF2gxgLv7E8pPR6MA
```

## Blacklisting IP addresses

This blacklisting urn could also be used to signal blacklisting of IP addresses, for instance IP addresses being used by known bot networks.

NOTE: Because IP addresses are personally identifiable information, we need to take more care when hashing these and publishing them on-chain to prevent reverse lookups of the IP addresses.

For IP addresses we could use a concatenation of the idKey of the signing party to add entropy to the hashing:
```
urn:bap:blacklist:ip-address:[IP Address]:[ID key]
```

This would prevent direct lookups and force services to only search for blacklisting by services they trust. A lookup of all attestations and all id's would be an extremly CPU intensive task.

For the Identity 1 (`3SyWUZXvhidNcEHbAC3HkBnKoD2Q`) blacklisting IP address `1.1.1.1`:
```
urn:bap:blacklist:ip-address:1.1.1.1:3SyWUZXvhidNcEHbAC3HkBnKoD2Q
```

The hash of this blacklisting is: `73df789478993f8f4e100be416811860d6fc2ae208fdfaf256788cd522f21219`

The attestation looks like:
```
1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMT
ATTEST
73df789478993f8f4e100be416811860d6fc2ae208fdfaf256788cd522f21219
0
|
15PciHG22SNLQJXMoSUaWVi7WSqc7hCfva
BITCOIN_ECDSA
1JfMQDtBKYi6z65M9uF2gxgLv7E8pPR6MA
HB6Ye7ekxjKDkblJYL9lX3J2vhY75vl+WfVCq+wW3+y6S7XECkgYwUEVH3WEArRuDb/aVZ8ntLI/D0Yolb1dhD8=
```

A third party service that wants to make use of this information is forced to look through a BAP index in a targeted way, per IP address and for each attesting service separately and is not able to recreate a list of blocked IP addresses.

## Final note on blacklisting

Using attestations for blacklists is a good way of creating one-way blacklists. It's easy to lookup whether some service has blacklisted something (transaction, address, IP address), but it is very hard to create a list of all things a service has blacklisted.

Also because the blacklist attestations look just like any other attestation, the blacklistings can not be identified as such which increases the difficulty of creating a list of  blacklistings of a service.

# Simple asserts

BAP can also be used for simple assertions of any type of data or action.

Example:
```
urn:bap:assert:[Some assertion you want to make]:[nonce]
```

Example, asserting ownership of a file:
```
urn:bap:assert:John Doe (john@example.com) owns the file with the sha256 hash 70eed96ac2900c68998a99166b4b4833ec311454dbbab82fc4ae028bbb802a35:02a043948c8185dab98456c20e5c68149e2fb9d52079068aeacb377240045515
```
This has a hash of `e4d1c8868d143cb8eaf240c0191c19e513a4f58a8eb8243af00b5626fe2eb764`.

The user then needs to attest to this on-chain, and sign:
```
1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMT
ATTEST
e4d1c8868d143cb8eaf240c0191c19e513a4f58a8eb8243af00b5626fe2eb764
0
|
15PciHG22SNLQJXMoSUaWVi7WSqc7hCfva
BITCOIN_ECDSA
1K4c6YXR1ixNLAqrL8nx5HUQAPKbACTwDo
HB6Ye7ekxjKDkblJYL9lX3J2vhY75vl+WfVCq+wW3+y6S7XECkgYwUEVH3WEArRuDb/aVZ8ntLI/D0Yolb1dhD8=
```

# Giving consent to access of data

When a website requests data from a user, the user should leave a record on-chain that this data was freely given to the service by the user. The user should also be able to revoke the access to the data, which would imply that the service needs to delete any copy's of the data shared. This is the only way the user is (legally) in charge of what data the service has access to.

A possible way to do this, using BAP:
```
urn:bap:grant:[Attribute names]:[Identity key]
```

Example, for a service with identity key `ezY2h8B5sj7SHGw8i1KhHtRvgM5`:
```
urn:bap:grant:name,email,alternateName:ezY2h8B5sj7SHGw8i1KhHtRvgM5
```
This has a hash of `b88bd23005be7e0737f02e67de8b392df834ba27caed1e7774aec77c9dcb85d0`.

The user then needs to attest to this on-chain:
```
1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMT
ATTEST
b88bd23005be7e0737f02e67de8b392df834ba27caed1e7774aec77c9dcb85d0
0
|
15PciHG22SNLQJXMoSUaWVi7WSqc7hCfva
BITCOIN_ECDSA
1K4c6YXR1ixNLAqrL8nx5HUQAPKbACTwDo
HB6Ye7ekxjKDkblJYL9lX3J2vhY75vl+WfVCq+wW3+y6S7XECkgYwUEVH3WEArRuDb/aVZ8ntLI/D0Yolb1dhD8=
```

NOTE: this could be given to the service directly as proof of granting access to the data by the user. The service could then put the data on-chain, paying the necessary fees.

The service should be monitoring the blockchain for a revocation of the data sharing grant and remove any data related to this user of the revocation is seen. Alternatively, the user could notify the service of a revocation transaction when made on-chain.

# Revoking an attestation

In rare cases when the attestation needs to be revoked, this can be done using the `REVOKE` keyword. The revocation transaction has exactly the same format as the attestation transaction, except for the REVOKE keyword.

```
1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMT
REVOKE
77cdec21e1025f85a5cb3744d5515c54783c739b8fa7c72c9e24d83900261d7f
1
|
15PciHG22SNLQJXMoSUaWVi7WSqc7hCfva
BITCOIN_ECDSA
1JfMQDtBKYi6z65M9uF2gxgLv7E8pPR6MA
HB6Ye7ekxjKDkblJYL9lX3J2vhY75vl+WfVCq+wW3+y6S7XECkgYwUEVH3WEArRuDb/aVZ8ntLI/D0Yolb1dhD8=
```

The sequence number is important here to prevent replays of the transaction.

# BAP on the BSV Metanet - PROVISIONAL

NOTE: This is still very much work in progress. Feedback is very welcome.

https://bitcoinsv.io/wp-content/uploads/2020/10/The-Metanet-Technical-Summary-v1.0.pdf

BAP transactions can also be published on-chain using the BSV metanet protocol. The biggest difference is that those transactions will not use the AIP signing protocol, but the native BSV transaction signing.

## An example.

A transaction like this, creating a new identity with root address `1WffojxvgpQBmUTigoss7VUdfN45JiiRK`:

```
1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMT
ID
3SyWUZXvhidNcEHbAC3HkBnKoD2Q
1KJ8vx8adZeznoDfoGf632rNkzxK2ZwzSG
|
15PciHG22SNLQJXMoSUaWVi7WSqc7hCfva
BITCOIN_ECDSA
1WffojxvgpQBmUTigoss7VUdfN45JiiRK
HB6Ye7ekxjKDkblJYL9lX3J2vhY75vl+WfVCq+wW3+y6S7XECkgYwUEVH3WEArRuDb/aVZ8ntLI/D0Yolb1dhD8=
```

Would then look more like this:

```
Input:
<𝑆𝑖𝑔 𝑃𝑝𝑎𝑟𝑒𝑛𝑡> <𝑃𝑝𝑎𝑟𝑒𝑛𝑡>
Output:
OP_RETURN meta <𝑃𝑛𝑜𝑑𝑒> <𝑇𝑥𝐼𝐷𝑝𝑎𝑟𝑒𝑛𝑡> 1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMT ID 3SyWUZXvhidNcEHbAC3HkBnKoD2Q
```

Where `Pparent` is the public key associated with the root address `1WffojxvgpQBmUTigoss7VUdfN45JiiRK`, and `Pnode` is the public key associated with the new signing address `1K4c6YXR1ixNLAqrL8nx5HUQAPKbACTwDo`.

Rotating to a new signing key (`1JfMQDtBKYi6z65M9uF2gxgLv7E8pPR6MA`) is now possible by creating a new metanet edge:

```
Input:
<𝑆𝑖𝑔 𝑃𝑝𝑎𝑟𝑒𝑛𝑡> <𝑃𝑝𝑎𝑟𝑒𝑛𝑡>
Output:
OP_RETURN meta <𝑃𝑛𝑜𝑑𝑒> <𝑇𝑥𝐼𝐷𝑝𝑎𝑟𝑒𝑛𝑡> 1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMT ID 3SyWUZXvhidNcEHbAC3HkBnKoD2Q
```

Where `Pparent` is the public key associated with the address `1K4c6YXR1ixNLAqrL8nx5HUQAPKbACTwDo`, and `Pnode` is the public key associated with the new signing address `1JfMQDtBKYi6z65M9uF2gxgLv7E8pPR6MA`.

An attestation could then be written like this on-chain:

```
Input:
<𝑆𝑖𝑔 𝑃𝑝𝑎𝑟𝑒𝑛𝑡> <𝑃𝑝𝑎𝑟𝑒𝑛𝑡>
Output:
OP_RETURN meta <𝑃𝑝𝑎𝑟𝑒𝑛𝑡> <𝑇𝑥𝐼𝐷𝑝𝑎𝑟𝑒𝑛𝑡> 1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMT ATTEST 2dbb381888f973a0db3bf311e551a6ac2f3ab792420262d8a6f65ef4feb8c1ef 0
```

Where `Pparent` is the public key associated with the latest valid address `1JfMQDtBKYi6z65M9uF2gxgLv7E8pPR6MA`. Note that the node links back to itself, creating a metanet leaf and not a new edge, where the `TxIDParent` points to the transaction where the edge was created.

# BAP w3c DID - PROVISIONAL

A w3c compatible DID can be created from a BAP ID in the following manner:

```
{
    "@context": ["https://w3id.org/did/v0.11", "https://w3id.org/bap/v1"],
    "id": "did:bap:id:<Identity Key>",
    "publicKey": [
       {
            "id": "did:bap:id:<Identity Key>#<key identifier>",
            "controller": "did:bap:id:<Identity Key>",
            "type": "EcdsaSecp256k1VerificationKey2019",
            "bitcoinAddress": "<Base58 bitcoin address>"
        }
    ],
    "authentication": ["#key1"],
    "assertionMethod": ["#key1"]
}
```
Example:
```
{
    "@context": ["https://w3id.org/did/v0.11", "https://w3id.org/bap/v1"],
    "id": "did:bap:id:3SyWUZXvhidNcEHbAC3HkBnKoD2Q",
    "publicKey": [
       {
            "id": "did:bap:id:3SyWUZXvhidNcEHbAC3HkBnKoD2Q#root",
            "controller": "did:bap:id:3SyWUZXvhidNcEHbAC3HkBnKoD2Q",
            "type": "EcdsaSecp256k1VerificationKey2019",
            "bitcoinAddress": "1WffojxvgpQBmUTigoss7VUdfN45JiiRK"
        },
       {
            "id": "did:bap:id:3SyWUZXvhidNcEHbAC3HkBnKoD2Q#key1",
            "controller": "did:bap:id:3SyWUZXvhidNcEHbAC3HkBnKoD2Q",
            "type": "EcdsaSecp256k1VerificationKey2019",
            "bitcoinAddress": "1K4c6YXR1ixNLAqrL8nx5HUQAPKbACTwDo"
        }
    ],
    "authentication": ["#key1"],
    "assertionMethod": ["#key1"]
}
```

When keys are rotated to a new signing key the new key can be added to the DID and all authentication and assertion set to that new key.
```
{
    "@context": ["https://w3id.org/did/v0.11", "https://w3id.org/bap/v1"],
    "id": "did:bap:id:3SyWUZXvhidNcEHbAC3HkBnKoD2Q",
    "publicKey": [
       {
            "id": "did:bap:id:3SyWUZXvhidNcEHbAC3HkBnKoD2Q#root",
            "controller": "did:bap:id:3SyWUZXvhidNcEHbAC3HkBnKoD2Q",
            "type": "EcdsaSecp256k1VerificationKey2019",
            "bitcoinAddress": "1WffojxvgpQBmUTigoss7VUdfN45JiiRK"
        },
       {
            "id": "did:bap:id:3SyWUZXvhidNcEHbAC3HkBnKoD2Q#key1",
            "controller": "did:bap:id:3SyWUZXvhidNcEHbAC3HkBnKoD2Q",
            "type": "EcdsaSecp256k1VerificationKey2019",
            "bitcoinAddress": "1K4c6YXR1ixNLAqrL8nx5HUQAPKbACTwDo"
        },
       {
            "id": "did:bap:id:3SyWUZXvhidNcEHbAC3HkBnKoD2Q#key2",
            "controller": "did:bap:id:3SyWUZXvhidNcEHbAC3HkBnKoD2Q",
            "type": "EcdsaSecp256k1VerificationKey2019",
            "bitcoinAddress": "1JfMQDtBKYi6z65M9uF2gxgLv7E8pPR6MA"
        }
    ],
    "authentication": ["#key2"],
    "assertionMethod": ["#key2"]
}
```

# Extending the protocol

The protocol could be extended for other use cases, by introducing new keywords (next to ATTEST, REVOKE, ID AND ALIAS) or introducing other `urn:bap:...` schemes.



