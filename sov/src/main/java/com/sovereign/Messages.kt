/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sovereign

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.apache.tuweni.bytes.Bytes32

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
@JsonSubTypes(JsonSubTypes.Type(value = StateRootData::class, name = "StateRoot"),
  JsonSubTypes.Type(value = StartChallenge::class, name = "StartChallenge"),
  JsonSubTypes.Type(value = BisectRequest::class, name = "BisectRequest"),
  JsonSubTypes.Type(value = BisectResponse::class, name = "BisectResponse"),
  JsonSubTypes.Type(value = ChallengeAccepted::class, name = "ChallengeAccepted"),
  JsonSubTypes.Type(value = AskForChallenger::class, name = "AskForChallenger"),
)
interface Message

class StateRootData : Message {

  @JsonProperty("root")
  var root: Bytes32? = null
}

class StartChallenge : Message {

  @JsonProperty("root")
  var root: Bytes32? = null
}

class ChallengeAccepted : Message {

  @JsonProperty("root")
  var root: Bytes32? = null
}

class BisectRequest: Message {
  @JsonProperty("root")
  var root: Bytes32? = null

  @JsonProperty("numInstructions")
  var numInstructions: Int = 0
}

class BisectResponse : Message {
  @JsonProperty("numInstructions")
  var numInstructions: Int = 0

  @JsonProperty("vmHash")
  var vmHash: Bytes32? = null

  @JsonProperty("complete")
  var complete: Boolean = false
}

class AskForChallenger : Message {
  @JsonProperty("root")
  var root: Bytes32? = null
}
