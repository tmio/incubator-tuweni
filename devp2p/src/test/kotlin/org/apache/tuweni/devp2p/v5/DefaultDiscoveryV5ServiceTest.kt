// Copyright The Tuweni Authors
// SPDX-License-Identifier: Apache-2.0
package org.apache.tuweni.devp2p.v5

import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.runBlocking
import org.apache.tuweni.bytes.Bytes
import org.apache.tuweni.concurrent.AsyncResult
import org.apache.tuweni.concurrent.coroutines.await
import org.apache.tuweni.crypto.SECP256K1
import org.apache.tuweni.devp2p.EthereumNodeRecord
import org.apache.tuweni.io.Base64URLSafe
import org.apache.tuweni.junit.BouncyCastleExtension
import org.apache.tuweni.junit.VertxExtension
import org.apache.tuweni.junit.VertxInstance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import java.net.InetAddress
import java.nio.ByteBuffer

@Timeout(10)
@ExtendWith(BouncyCastleExtension::class, VertxExtension::class)
class DefaultDiscoveryV5ServiceTest {

  private val recipientKeyPair: SECP256K1.KeyPair = SECP256K1.KeyPair.random()
  private val recipientEnr: Bytes =
    EthereumNodeRecord.toRLP(recipientKeyPair, ip = InetAddress.getLoopbackAddress(), udp = 19001)
  private val encodedEnr: String = "enr:${Base64URLSafe.encode(recipientEnr)}"
  private val keyPair: SECP256K1.KeyPair = SECP256K1.KeyPair.random()
  private val localPort: Int = 19000
  private val bootstrapENRList: List<String> = listOf(encodedEnr)

  @Test
  fun startInitializesConnectorAndBootstraps(@VertxInstance vertx: Vertx): Unit = runBlocking {
    val reference = AsyncResult.incomplete<Buffer>()
    val client = vertx.createDatagramSocket().handler { res ->
      reference.complete(res.data())
    }.listen(19001, "localhost").coAwait()
    val discoveryV5Service: DiscoveryV5Service =
      DiscoveryService.open(
        vertx,
        keyPair,
        localPort,
        bootstrapENRList = bootstrapENRList,
      )
    discoveryV5Service.start()

    val datagram = reference.await()
    val buffer = ByteBuffer.allocate(datagram.length())
    buffer.put(datagram.bytes)
    buffer.flip()
    val receivedBytes = Bytes.wrapByteBuffer(buffer)
    val content = receivedBytes.slice(45)

    val message = RandomMessage.create(
      Message.authTag(),
      content,
    )
    assertEquals(message.data.size(), Message.RANDOM_DATA_LENGTH)
    discoveryV5Service.terminate()
    client.close()
  }
}
