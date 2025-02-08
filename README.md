# Tuweni: Blockchain Libraries for Java (& Kotlin)

[![Github build](https://github.com/tmio/tuweni/actions/workflows/build.yml/badge.svg)](https://github.com/tmio/tuweni/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/tmio/tuweni/blob/main/LICENSE)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.tmio/tuweni-tuweni/badge.svg?style=plastic)](https://maven-badges.herokuapp.com/maven-central/io.tmio/tuweni-tuweni)

![logo](tuweni.png)

See our [website](https://tuweni.tmio.io) for details on the project.

Tuweni is a set of libraries and other tools to aid development of blockchain and other decentralized software in Java and other JVM languages.

It includes a low-level bytes library, serialization and deserialization codecs (e.g. [RLP](https://github.com/ethereum/wiki/wiki/RLP)), various cryptography functions and primatives, and lots of other helpful utilities.

Tuweni is developed for JDK 11 or higher.

## Clone along with submodules ##
    git clone https://github.com/tmio/tuweni.git tuweni
    cd tuweni
    git submodule update --init --recursive

### Build the project ###
#### With Gradle and Java ####
Install JDK 11.

Run:

`$>./gradlew build`

After a successful build, libraries will be available in `build/libs`.

## Contributing

Your contributions are very welcome! Here are a few links to help you:

- [Issue tracker: Report a defect or feature request](https://www.github.com/tmio/tuweni/issues)
- [StackOverflow: Ask "how-to" and "why-didn't-it-work" questions](https://stackoverflow.com/questions/ask?tags=tuweni)

## More information

- [Official website](https://tuweni.tmio.io)
- [GitHub project](https://github.com/tmio/tuweni)

# License

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
specific language governing permissions and limitations under the License.

# Crypto Notice

This distribution includes cryptographic software. The country in which you
currently reside may have restrictions on the import, possession, use, and/or
re-export to another country, of encryption software. BEFORE using any
encryption software, please check your country's laws, regulations and
policies concerning the import, possession, or use, and re-export of encryption
software, to see if this is permitted. See [http://www.wassenaar.org] for
more information.

The Apache Software Foundation has classified this software as Export Commodity
Control Number (ECCN) 5D002, which includes information security software using
or performing cryptographic functions with asymmetric algorithms. The form and
manner of this Apache Software Foundation distribution makes it eligible for
export under the "publicly available" Section 742.15(b) exemption (see the BIS
Export Administration Regulations, Section 742.15(b)) for both object code and
source code.
The following provides more details on the included cryptographic software:
* [Bouncy Castle](http://bouncycastle.org/)
* [Tuweni crypto](./crypto)
