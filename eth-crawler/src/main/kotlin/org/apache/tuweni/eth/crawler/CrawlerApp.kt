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
package org.apache.tuweni.eth.crawler

import com.zaxxer.hikari.HikariDataSource
import io.vertx.core.Vertx
import io.vertx.core.net.SocketAddress
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.tuweni.concurrent.coroutines.await
import org.apache.tuweni.crypto.SECP256K1
import org.apache.tuweni.devp2p.Scraper
import org.apache.tuweni.devp2p.eth.EthHelloSubprotocol
import org.apache.tuweni.devp2p.eth.SimpleBlockchainInformation
import org.apache.tuweni.devp2p.eth.logger
import org.apache.tuweni.eth.genesis.GenesisFile
import org.apache.tuweni.peer.repository.RelationalPeerRepository
import org.apache.tuweni.rlpx.RLPxService
import org.apache.tuweni.rlpx.vertx.VertxRLPxService
import org.apache.tuweni.rlpx.wire.DisconnectReason
import org.apache.tuweni.units.bigints.UInt256
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Paths
import java.security.Security
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Application running as a daemon and quietly collecting information about Ethereum nodes.
 */
object CrawlerApp {

  val logger = LoggerFactory.getLogger(CrawlerApp.javaClass)

  @JvmStatic
  fun main(args: Array<String>) {
    val configFile = Paths.get(if (args.isNotEmpty()) args[0] else "config.toml")
    Security.addProvider(BouncyCastleProvider())
    val vertx = Vertx.vertx()
    val config = CrawlerConfig(configFile)
    if (config.config.hasErrors()) {
      for (error in config.config.errors()) {
        println(error.message)
      }
      System.exit(1)
    }
    run(vertx, config)
  }

  fun run(vertx: Vertx, config: CrawlerConfig) {
    val ds = HikariDataSource()
    ds.jdbcUrl = config.jdbcUrl()
    val flyway = Flyway.configure()
      .dataSource(ds)
      .load()
    flyway.migrate()

    val repo = RelationalPeerRepository(ds)

    logger.info("Initial bootnodes: ${config.bootNodes()}")
    val scraper = Scraper(
      vertx = vertx,
      initialURIs = config.bootNodes(),
      bindAddress = SocketAddress.inetSocketAddress(config.discoveryPort(), config.discoveryNetworkInterface()),
      repository = repo
    )
    val contents = if (config.network() == null) {
      Files.readAllBytes(Paths.get(config.genesisFile()))
    } else {
      CrawlerApp::class.java.getResourceAsStream("/${config.network()}.json").readAllBytes()
    }

    val genesisFile = GenesisFile.read(contents)
    val genesisBlock = genesisFile.toBlock()
    val blockchainInformation = SimpleBlockchainInformation(
      UInt256.valueOf(genesisFile.chainId.toLong()), genesisBlock.header.difficulty,
      genesisBlock.header.hash, UInt256.valueOf(42L), genesisBlock.header.hash, genesisFile.forks
    )

    val ethHelloProtocol = EthHelloSubprotocol(
      blockchainInfo = blockchainInformation,
      listener = { conn, status ->
        repo.recordInfo(conn, status)
      }
    )

    val rlpxService = VertxRLPxService(vertx, 0, "127.0.0.1", 0, SECP256K1.KeyPair.random(), listOf(ethHelloProtocol), "Apache Tuweni network crawler")
    repo.addListener {
      connect(rlpxService, it.nodeId, InetSocketAddress(it.endpoint.address, it.endpoint.tcpPort ?: 30303))
    }
    val restService = CrawlerRESTService(port = config.restPort(), networkInterface = config.restNetworkInterface(), repository = repo)
    val refreshLoop = AtomicBoolean(true)

    Runtime.getRuntime().addShutdownHook(
      Thread {
        runBlocking {
          refreshLoop.set(false)
          scraper.stop().await()
          rlpxService.stop().await()
          restService.stop().await()
        }
      }
    )
    runBlocking {
      restService.start().await()
      launch {
        while (refreshLoop.get()) {
          try {
            for (connectionInfo in repo.getPeers(System.currentTimeMillis() - 60 * 60 * 1000)) {
              connect(rlpxService, connectionInfo.nodeId, InetSocketAddress(connectionInfo.host, if (connectionInfo.port == 0) 30303 else connectionInfo.port))
            }
          } catch (e: Exception) {
            logger.error("Error connecting to pending peers", e)
          }
          delay(5 * 60 * 1000)
        }
      }
      rlpxService.start().await()
      scraper.start()
      launch {
        while (refreshLoop.get()) {
          try {
            for (connectionInfo in repo.getPendingPeers()) {
              connect(
                rlpxService,
                connectionInfo.nodeId,
                InetSocketAddress(connectionInfo.host, if (connectionInfo.port == 0) 30303 else connectionInfo.port)
              )
            }
          } catch (e: Exception) {
            logger.error("Error connecting to pending peers", e)
          }
          delay(2 * 60 * 1000)
        }
      }
    }
  }
}

fun connect(rlpxService: RLPxService, key: SECP256K1.PublicKey, address: InetSocketAddress) {
  rlpxService.connectTo(key, address).thenAccept {
    rlpxService.disconnect(it, DisconnectReason.CLIENT_QUITTING)
  }
}
