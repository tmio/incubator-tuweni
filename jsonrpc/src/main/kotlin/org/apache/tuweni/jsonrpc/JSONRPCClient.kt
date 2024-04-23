// Copyright The Tuweni Authors
// SPDX-License-Identifier: Apache-2.0
package org.apache.tuweni.jsonrpc

import com.fasterxml.jackson.databind.ObjectMapper
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.tracing.TracingPolicy
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import org.apache.tuweni.eth.Address
import org.apache.tuweni.eth.EthJsonModule
import org.apache.tuweni.eth.JSONRPCRequest
import org.apache.tuweni.eth.JSONRPCResponse
import org.apache.tuweni.eth.StringOrLong
import org.apache.tuweni.eth.Transaction
import org.apache.tuweni.units.bigints.UInt256
import java.io.Closeable
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

val mapper = ObjectMapper()

/**
 * JSON-RPC client to send requests to an Ethereum client.
 */
class JSONRPCClient(
  vertx: Vertx,
  val endpointUrl: String,
  val userAgent: String = "Apache Tuweni JSON-RPC Client",
  val basicAuthenticationEnabled: Boolean = false,
  val basicAuthenticationUsername: String = "",
  val basicAuthenticationPassword: String = "",
  override val coroutineContext: CoroutineContext = vertx.dispatcher(),
) : Closeable, CoroutineScope {

  companion object {
    private val mapper = ObjectMapper()
    init {
      mapper.registerModule(EthJsonModule())
    }
  }
  val requestCounter = AtomicLong(1)
  val client = WebClient.create(
    vertx,
    WebClientOptions().setUserAgent(userAgent).setDecompressionSupported(true)
      .setTracingPolicy(TracingPolicy.ALWAYS) as WebClientOptions,
  )
  val authorizationHeader = "Basic " + Base64.getEncoder()
    .encode((basicAuthenticationUsername + ":" + basicAuthenticationPassword).toByteArray())

  fun sendRequest(request: JSONRPCRequest): Deferred<JSONRPCResponse> {
    val deferred = CompletableDeferred<JSONRPCResponse>()
    val httpRequest = client.postAbs(endpointUrl)
      .putHeader("Content-Type", "application/json")

    if (basicAuthenticationEnabled) {
      httpRequest.putHeader("authorization", authorizationHeader)
    }

    httpRequest.sendBuffer(Buffer.buffer(mapper.writeValueAsBytes(request))) { response ->
      if (response.failed()) {
        deferred.completeExceptionally(response.cause())
      } else {
        val jsonResponse = mapper.readValue(response.result().bodyAsString(), JSONRPCResponse::class.java)
        deferred.complete(jsonResponse)
      }
    }

    return deferred
  }

  /**
   * Sends a signed transaction to the Ethereum network.
   * @param tx the transaction object to send
   * @return the hash of the transaction, or an empty string if the hash is not available yet.
   * @throws ClientRequestException if the request is rejected
   * @throws ConnectException if it cannot dial the remote client
   */
  suspend fun sendRawTransaction(tx: Transaction): String {
    val body = JSONRPCRequest(StringOrLong(nextId()), "eth_sendRawTransaction", arrayOf(tx.toBytes().toHexString()))
    val jsonResponse = sendRequest(body).await()
    val err = jsonResponse.error
    if (err != null) {
      val errorMessage = "Code ${err.code}: ${err.message}"
      throw ClientRequestException(errorMessage)
    } else {
      return jsonResponse.result.toString()
    }
  }

  /**
   * Gets the account balance.
   * @param tx the transaction object to send
   * @return the hash of the transaction, or an empty string if the hash is not available yet.
   * @throws ClientRequestException if the request is rejected
   * @throws ConnectException if it cannot dial the remote client
   */
  suspend fun getBalance_latest(address: Address): UInt256 {
    val body = JSONRPCRequest(StringOrLong(nextId()), "eth_getBalance", arrayOf(address.toHexString(), "latest"))
    val jsonResponse = sendRequest(body).await()
    val err = jsonResponse.error
    if (err != null) {
      val errorMessage = "Code ${err.code}: ${err.message}"
      throw ClientRequestException(errorMessage)
    } else {
      return UInt256.fromHexString(jsonResponse.result.toString())
    }
  }

  /**
   * Gets the number of transactions sent from an address.
   * @param tx the transaction object to send
   * @return the hash of the transaction, or an empty string if the hash is not available yet.
   * @throws ClientRequestException if the request is rejected
   * @throws ConnectException if it cannot dial the remote client
   */
  suspend fun getTransactionCount_latest(address: Address): UInt256 {
    val body = JSONRPCRequest(
      StringOrLong(nextId()),
      "eth_getTransactionCount",
      arrayOf(address.toHexString(), "latest"),
    )
    val jsonResponse = sendRequest(body).await()
    val err = jsonResponse.error
    if (err != null) {
      val errorMessage = "Code ${err.code}: ${err.message}"
      throw ClientRequestException(errorMessage)
    } else {
      return UInt256.fromHexString(jsonResponse.result.toString())
    }
  }

  /**
   * Gets block data by block number
   * @param blockNumber the block number
   * @param includeTransactions whether to include transactions detail
   * @return the whole block information as a JSON document.
   */
  suspend fun getBlockByBlockNumber(blockNumber: Int, includeTransactions: Boolean): Map<*, *> {
    val body = JSONRPCRequest(
      StringOrLong(nextId()),
      "eth_getBlockByNumber",
      arrayOf(UInt256.valueOf(blockNumber.toLong()).toShortHexString(), includeTransactions),
    )
    val jsonResponse = sendRequest(body).await()
    val err = jsonResponse.error
    if (err != null) {
      val errorMessage = "Code ${err.code}: ${err.message}"
      throw ClientRequestException(errorMessage)
    } else {
      return jsonResponse.result as Map<*, *>
    }
  }

  override fun close() {
    client.close()
  }

  private fun nextId(): Long {
    val next = requestCounter.incrementAndGet()
    if (next == Long.MAX_VALUE) {
      requestCounter.set(1)
    }
    return next
  }
}
