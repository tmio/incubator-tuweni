// Copyright The Tuweni Authors
// SPDX-License-Identifier: Apache-2.0
package org.apache.tuweni.stratum.server

import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.net.KeyCertOptions
import io.vertx.core.net.NetServer
import io.vertx.core.net.NetServerOptions
import io.vertx.core.net.NetSocket
import io.vertx.core.net.SelfSignedCertificate
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.tuweni.bytes.Bytes32
import org.apache.tuweni.concurrent.ExpiringSet
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

/**
 * Simple main function to run the server with a self-signed certificate.
 */
fun main() = runBlocking {
  val selfSignedCertificate = SelfSignedCertificate.create()
  val server = StratumServer(
    Vertx.vertx(),
    port = 10000,
    networkInterface = "0.0.0.0",
    sslOptions = selfSignedCertificate.keyCertOptions(),
    submitCallback = { true },
    seedSupplier = { Bytes32.random() },
  )
  server.start()
  Runtime.getRuntime().addShutdownHook(
    Thread {
      runBlocking {
        StratumServer.logger.info("Shutting down...")
        server.stop()
      }
    },
  )
}

/**
 * Server capable of handling connections from Stratum clients, authenticate and serve content for them.
 */
class StratumServer(
  val vertx: Vertx,
  private val port: Int,
  val networkInterface: String,
  private val sslOptions: KeyCertOptions?,
  extranonce: String = "",
  submitCallback: suspend (PoWSolution) -> Boolean,
  seedSupplier: () -> Bytes32,
  private val errorThreshold: Int = 3,
  denyTimeout: Long = 600000, // 10 minutes in milliseconds
  override val coroutineContext: CoroutineContext = vertx.dispatcher(),
) : CoroutineScope {

  companion object {
    val logger = LoggerFactory.getLogger(StratumServer::class.java)
  }

  private val protocols: Array<StratumProtocol> = arrayOf(
    Stratum1Protocol(
      extranonce,
      submitCallback = submitCallback,
      seedSupplier = seedSupplier,
      coroutineContext = this.coroutineContext,
    ),
    Stratum1EthProxyProtocol(submitCallback, seedSupplier, this.coroutineContext),
  )

  private val started = AtomicBoolean(false)
  private var tcpServer: NetServer? = null
  private val denyList = ExpiringSet<String>(denyTimeout)

  /**
   * Accepts new work. sending it to all connected clients. New clients will also be notified of this work.
   *
   * This method returns immediately, all messages are sent asynchronously.
   *
   * @param powInput the new work
   */
  fun setNewWork(powInput: PoWInput) {
    for (protocol in protocols) {
      launch {
        protocol.setCurrentWorkTask(powInput)
      }
    }
  }

  suspend fun start() {
    if (started.compareAndSet(false, true)) {
      val options = NetServerOptions().setPort(port).setHost(networkInterface).setTcpKeepAlive(true)
      sslOptions?.let {
        options.setKeyCertOptions(it)
      }
      val server = vertx.createNetServer(options)
      server.exceptionHandler { e -> logger.error(e.message, e) }
      server.connectHandler(this::handleConnection)
      server.listen().coAwait()
      tcpServer = server
    }
  }

  private fun handleConnection(socket: NetSocket) {
    val name = socket.remoteAddress().host() + ":" + socket.remoteAddress().port()
    if (denyList.contains(socket.remoteAddress().host())) {
      logger.warn("$name attempted to reconnect while denied, booting")
      socket.close()
      return
    }
    socket.exceptionHandler { e -> logger.error(e.message, e) }
    val conn = StratumConnection(
      protocols,
      closeHandle = { addToDenyList ->
        if (addToDenyList) {
          denyList.add(socket.remoteAddress().host())
        }
        socket.close()
      },
      name = name,
      threshold = errorThreshold,
      sender = { bytes -> socket.write(Buffer.buffer(bytes)) },
    )
    socket.handler(conn::handleBuffer)
    socket.closeHandler {
      logger.trace("Client initiated socket close $name")
      conn.close(false)
    }
  }

  suspend fun stop() {
    if (started.compareAndSet(true, false)) {
      tcpServer?.close()?.coAwait()
    }
  }

  fun port(): Int {
    return tcpServer?.actualPort() ?: port
  }
}
