// Copyright The Tuweni Authors
// SPDX-License-Identifier: Apache-2.0
package org.apache.tuweni.devp2p

import org.apache.tuweni.bytes.Bytes
import org.apache.tuweni.bytes.Bytes32
import org.apache.tuweni.crypto.SECP256K1

private const val MAX_VALUE: Byte = 0x7f

/**
 * DevP2P discovery packet types
 * @param typeId the byte representing the type
 */
internal enum class PacketType(
  val typeId: Byte,
) {
  /**
   * Ping packet
   */
  PING(0x01) {
    override fun decode(
      payload: Bytes,
      hash: Bytes32,
      publicKey: SECP256K1.PublicKey,
      signature: SECP256K1.Signature,
    ) = PingPacket.decode(payload, hash, publicKey, signature) as Packet
  },

  /**
   * Pong packet as response to pings
   */
  PONG(0x02) {
    override fun decode(
      payload: Bytes,
      hash: Bytes32,
      publicKey: SECP256K1.PublicKey,
      signature: SECP256K1.Signature,
    ) = PongPacket.decode(payload, hash, publicKey, signature) as Packet
  },

  /**
   * FindNode packet
   */
  FIND_NODE(0x03) {
    override fun decode(
      payload: Bytes,
      hash: Bytes32,
      publicKey: SECP256K1.PublicKey,
      signature: SECP256K1.Signature,
    ) = FindNodePacket.decode(payload, hash, publicKey, signature) as Packet
  },

  /**
   * Neighbors packet response
   */
  NEIGHBORS(0x04) {
    override fun decode(
      payload: Bytes,
      hash: Bytes32,
      publicKey: SECP256K1.PublicKey,
      signature: SECP256K1.Signature,
    ) = NeighborsPacket.decode(payload, hash, publicKey, signature) as Packet
  },

  /**
   * ENR request packet
   */
  ENRREQUEST(0x05) {
    override fun decode(
      payload: Bytes,
      hash: Bytes32,
      publicKey: SECP256K1.PublicKey,
      signature: SECP256K1.Signature,
    ) = ENRRequestPacket.decode(payload, hash, publicKey, signature) as Packet
  },

  /**
   * ENR response packet
   */
  ENRRESPONSE(0x06) {
    override fun decode(
      payload: Bytes,
      hash: Bytes32,
      publicKey: SECP256K1.PublicKey,
      signature: SECP256K1.Signature,
    ) = ENRResponsePacket.decode(payload, hash, publicKey, signature) as Packet
  }, ;

  companion object {
    private val INDEX = arrayOfNulls<PacketType?>(MAX_VALUE.toInt())

    init {
      // populate an array by packet type id for index-based lookup in `forType(Byte)`
      PacketType.values().forEach { type -> INDEX[type.typeId.toInt()] = type }
    }

    fun forType(typeId: Byte): PacketType? {
      return INDEX[typeId.toInt()]
    }
  }

  init {
    require(typeId <= MAX_VALUE) { "Packet typeId must be in range [0x00, 0x80)" }
  }

  abstract fun decode(
    payload: Bytes,
    hash: Bytes32,
    publicKey: SECP256K1.PublicKey,
    signature: SECP256K1.Signature,
  ): Packet
}
