<!---
Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
License. You may obtain a copy of the License at
 *
http://www.apache.org/licenses/LICENSE-2.0
 *
Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
specific language governing permissions and limitations under the License.
 --->
# Ethereum Faucet

| Status         |               |
|----------------|---------------|
| Stability      | [alpha]       |
| Component Type | [application] |

This example allows you to set up a faucet with Github authentication.

The application is written in Kotlin with Spring Boot, with Spring Web, Spring Security and Thymeleaf templates.

## Configuration

The faucet works with a configuration file, `application.yml`. Here is a template of the file:

```yaml
server:
  use-forward-headers: true
  forward-headers-strategy: native
spring:
  main:
    banner-mode: "off"
  security:
    oauth2:
      client:
        registration:
          github:
            clientId: <your github client ID>
            clientSecret: <your github client secret>
html:
  title: Faucet
  request_message: Welcome to our faucet. You can ask for up to 1 ETH on this faucet.

security:
  oauth2:
    client:
      preEstablishedRedirectUri: <registered github redirect URI>
      registeredRedirectUri: <registered github redirect URI>
      useCurrentUri: false

faucet:
  maxETH: <the maximum amount of eth, in ETH, that you>
  chainId: <chain id of your network>
  rpcPort: <Ethereum client RPC port>
  rpcHost: <Ethereum client RPC host>

wallet:
  path: wallet.key
banner: >
  Apache Tuweni Faucet example.


           `:oyhdhhhhhhyo-`
         :yds/.        ./sdy:
       :mh:                :hm:
     `ym:                    :my`
     hm`                      `mh
    +N.                        .N+
    my :ydh/              /hdy- ym
    Mo`MMMMM`            .MMMMN oM
    my /hdh/              +hdh: ym
    +N.                        .N+
     hm`              `m:     `mh
     `ym:    `sssssssssN:    :my`
       :dh:   ``````````   :hd:
         :yds/.        ./sdy:
           `-oyhdhhhhdhyo-`
```

You should register a Github OAuth application to go along and fill the template.

# Faucet

This web application creates an account on chain and allows folks to request money from it.

The faucet will top up their accounts up to to max balance that is permitted.

## Running locally

Start the faucet with the script in the distribution `eth-faucet`.

You will need to pass in a wallet password.

```
$> ./eth-faucet --wallet.password=changeit
```

If it is the first time the application runs, the wallet is created.

Navigate to localhost:8080 and sign in using github.

You will then be greeted to a page where you can ask for funds.

In parallel, start Hyperledger Besu:

`$> besu --network=dev --rpc-http-enabled --host-allowlist=* --rpc-http-cors-origins=* --miner-enabled --miner-coinbase 0xfe3b557e8fb62b89f4916b721be55ceb828dbd73`

This allows to run Besu with just one node.

In the web page, note the faucet account address. Make sure to send money to that faucet account (you can use Metamask for this, and the dev network private keys are documented).

Now you can send money using the faucet. Enter any valid address and press OK.

The second time you ask for money, the faucet will detect the balance of the account matches the max the faucet with top up.

[alpha]:https://github.com/tmio/tuweni/tree/main/docs/index.md#alpha
[application]:https://github.com/tmio/tuweni/tree/main/docs/index.md#application