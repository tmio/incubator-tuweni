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
package org.apache.tuweni.blockprocessor

import org.apache.tuweni.eth.AccountState
import org.apache.tuweni.eth.Address
import org.apache.tuweni.eth.Block
import org.apache.tuweni.eth.Hash
import org.apache.tuweni.eth.LogsBloomFilter
import org.apache.tuweni.eth.Transaction
import org.apache.tuweni.eth.TransactionReceipt
import org.apache.tuweni.eth.repository.BlockchainRepository
import org.apache.tuweni.eth.repository.TransientStateRepository
import org.apache.tuweni.evm.EVMExecutionStatusCode
import org.apache.tuweni.evm.EthereumVirtualMachine
import org.apache.tuweni.evm.impl.EvmVmImpl
import org.apache.tuweni.rlp.RLP
import org.apache.tuweni.trie.MerklePatriciaTrie
import org.apache.tuweni.trie.MerkleTrie
import org.apache.tuweni.units.bigints.UInt256
import org.apache.tuweni.units.ethereum.Gas
import org.apache.tuweni.units.ethereum.Wei
import java.time.Instant

/**
 * A block processor executing blocks, executing in sequence transactions
 * and committing data to a blockchain repository.
 */
class BlockProcessor {

  suspend fun execute(parentBlock: Block, transactions: List<Transaction>, repository: BlockchainRepository): ProtoBlock {
    val stateChanges = TransientStateRepository(repository)
    val vm = EthereumVirtualMachine(repository, EvmVmImpl::create)
    vm.start()
    var index = 0L

    val bloomFilter = LogsBloomFilter()

    val transactionsTrie = MerklePatriciaTrie.storingBytes()
    val receiptsTrie = MerklePatriciaTrie.storingBytes()
    val allReceipts = mutableListOf<TransactionReceipt>()

    var counter = 0L
    var allGasUsed = Gas.ZERO
    for (tx in transactions) {
      val indexKey = RLP.encodeValue(UInt256.valueOf(counter).trimLeadingZeros())
      transactionsTrie.put(indexKey, tx.toBytes())
      if (null == tx.to) {
        val contractAddress = Address.fromBytes(
          Hash.hash(
            RLP.encodeList {
              it.writeValue(tx.sender!!)
              it.writeValue(tx.nonce)
            }
          ).slice(12)
        )
        val state = AccountState(
          UInt256.ONE,
          Wei.valueOf(0),
          Hash.fromBytes(MerkleTrie.EMPTY_TRIE_ROOT_HASH),
          Hash.hash(tx.payload)
        )
        stateChanges.storeAccount(contractAddress, state)
        stateChanges.storeCode(tx.payload)
        val receipt = TransactionReceipt(
          1,
          0, // TODO
          LogsBloomFilter(),
          emptyList()
        )
        allReceipts.add(receipt)
        receiptsTrie.put(indexKey, receipt.toBytes())
        counter++
      } else {
        val code = stateChanges.getAccountCode(tx.to!!)
        val result = vm.execute(
          tx.sender!!,
          tx.to!!,
          tx.value,
          code!!,
          tx.payload,
          parentBlock.header.gasLimit,
          tx.gasPrice,
          Address.ZERO,
          index,
          Instant.now().toEpochMilli(),
          tx.gasLimit.toLong(),
          parentBlock.header.difficulty
        )
        if (result.statusCode != EVMExecutionStatusCode.SUCCESS) {
          throw Exception("invalid transaction result")
        }
        for (balanceChange in result.changes.getBalanceChanges()) {
          val state = stateChanges.getAccount(balanceChange.key)?.let {
            AccountState(it.nonce, balanceChange.value, it.storageRoot, it.codeHash)
          } ?: stateChanges.newAccountState()
          stateChanges.storeAccount(balanceChange.key, state)
        }

        for (storageChange in result.changes.getAccountChanges()) {
          for (oneStorageChange in storageChange.value) {
            stateChanges.storeAccountValue(storageChange.key, oneStorageChange.key, oneStorageChange.value)
          }
        }

        for (accountToDestroy in result.changes.accountsToDestroy()) {
          stateChanges.destroyAccount(accountToDestroy)
        }
        for (log in result.changes.getLogs()) {
          bloomFilter.insertLog(log)
        }

        val txLogsBloomFilter = LogsBloomFilter()
        for (log in result.changes.getLogs()) {
          bloomFilter.insertLog(log)
        }
        val receipt = TransactionReceipt(
          1,
          result.gasManager.gasCost.toLong(),
          txLogsBloomFilter,
          result.changes.getLogs()
        )
        allReceipts.add(receipt)
        receiptsTrie.put(indexKey, receipt.toBytes())
        counter++

        allGasUsed = allGasUsed.add(result.gasManager.gasCost)
      }
    }

    val block = ProtoBlock(
      SealableHeader(
        parentBlock.header.hash,
        Hash.fromBytes(stateChanges.stateRootHash()),
        Hash.fromBytes(transactionsTrie.rootHash()),
        Hash.fromBytes(receiptsTrie.rootHash()),
        bloomFilter.toBytes(),
        parentBlock.header.number.add(1),
        parentBlock.header.gasLimit,
        allGasUsed,
      ),
      ProtoBlockBody(transactions),
      allReceipts,
      stateChanges
    )
    return block
  }
}
