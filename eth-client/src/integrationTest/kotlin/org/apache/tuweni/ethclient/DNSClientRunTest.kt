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
package org.apache.tuweni.ethclient

import kotlinx.coroutines.runBlocking
import org.apache.tuweni.kv.MapKeyValueStore
import org.apache.tuweni.peer.repository.MemoryEthereumPeerRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DNSClientRunTest {
  @Test
  fun testStartAndStop() {
    val client = DNSClient(
      DNSConfigurationImpl("default", "foo", "example.com", 1000),
      MapKeyValueStore.open(), MemoryEthereumPeerRepository()
    )
    runBlocking {
      client.start()
      client.stop()
    }
  }

  @Test
  fun changeSeq() {
    val client = DNSClient(
      DNSConfigurationImpl("default", "foo", "example.com", 1000),
      MapKeyValueStore.open(), MemoryEthereumPeerRepository()
    )
    runBlocking {
      client.seq(42L)
      assertEquals(42L, client.seq())
    }
  }
}
