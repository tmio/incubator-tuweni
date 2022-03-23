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
package org.apache.tuweni.evm

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.apache.lucene.index.IndexWriter
import org.apache.tuweni.bytes.Bytes
import org.apache.tuweni.bytes.Bytes32
import org.apache.tuweni.eth.AccountState
import org.apache.tuweni.eth.Address
import org.apache.tuweni.eth.EthJsonModule
import org.apache.tuweni.eth.Hash
import org.apache.tuweni.eth.repository.BlockchainRepository
import org.apache.tuweni.evm.impl.EvmVmImpl
import org.apache.tuweni.genesis.Genesis
import org.apache.tuweni.io.Resources
import org.apache.tuweni.junit.BouncyCastleExtension
import org.apache.tuweni.junit.LuceneIndexWriter
import org.apache.tuweni.junit.LuceneIndexWriterExtension
import org.apache.tuweni.trie.MerklePatriciaTrie
import org.apache.tuweni.trie.MerkleTrie
import org.apache.tuweni.units.bigints.UInt256
import org.apache.tuweni.units.ethereum.Gas
import org.apache.tuweni.units.ethereum.Wei
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.IOException
import java.io.InputStream
import java.io.UncheckedIOException
import java.util.stream.Stream

@ExtendWith(LuceneIndexWriterExtension::class, BouncyCastleExtension::class)
class EVMReferenceTest {

  companion object {

    val mapper = ObjectMapper()

    init {
      mapper.registerModule(EthJsonModule())
    }

    @JvmStatic
    @Throws(IOException::class)
    private fun findBerlinTests(): Stream<Arguments> {
      return findTests("/berlin/VMTests/**/*.json")
    }

    @JvmStatic
    @Throws(IOException::class)
    private fun findIstanbulTests(): Stream<Arguments> {
      return findTests("/istanbul/VMTests/**/*.json")
    }

    @Throws(IOException::class)
    private fun findTests(glob: String): Stream<Arguments> {
      return Resources.find(glob).filter { !(it.file.contains("loop") && it.file.contains("10M")) }.flatMap { url ->
        try {
          url.openConnection().getInputStream().use { input -> prepareTests(input) }
        } catch (e: IOException) {
          throw UncheckedIOException("Could not read $url", e)
        }
      }
    }

    @Throws(IOException::class)
    private fun prepareTests(input: InputStream): Stream<Arguments> {

      val typeRef = object : TypeReference<HashMap<String, JsonReferenceTest>>() {}
      val allTests: Map<String, JsonReferenceTest> = mapper.readValue(input, typeRef)
      return allTests
        .entries
        .stream()
        .map { entry ->
          Arguments.of(entry.key, entry.value)
        }
    }
  }

  private var writer: IndexWriter? = null

  @BeforeEach
  fun setUp(@LuceneIndexWriter newWriter: IndexWriter) {
    writer = newWriter
  }

  @ParameterizedTest(name = "Berlin {index}: {0}")
  @MethodSource("findBerlinTests")
  fun runBerlinReferenceTests(testName: String, test: JsonReferenceTest) {
    runReferenceTests(testName, test)
  }

  @ParameterizedTest(name = "Istanbul {index}: {0}")
  @MethodSource("findIstanbulTests")
  fun runIstanbulReferenceTests(testName: String, test: JsonReferenceTest) {
    runReferenceTests(testName, test)
  }

  private fun runReferenceTests(testName: String, test: JsonReferenceTest) = runBlocking {
    assertNotNull(testName)
    println(testName)
    val repository = BlockchainRepository.inMemory(Genesis.dev())
    test.pre!!.forEach { address, state ->
      runBlocking {
        val accountState = AccountState(state.nonce!!, state.balance!!, Hash.fromBytes(MerkleTrie.EMPTY_TRIE_ROOT_HASH), Hash.hash(state.code!!))
        repository.storeAccount(address, accountState)
        repository.storeCode(state.code!!)
        val accountStorage = state.storage

        if (accountStorage != null) {
          for (entry in accountStorage) {
            repository.storeAccountValue(address, Bytes32.leftPad(entry.key), Bytes32.leftPad(entry.value))
          }
        }
      }
    }
    val vm = EthereumVirtualMachine(repository, EvmVmImpl::create)
    vm.start()
    try {
      val result = vm.execute(
        test.exec?.origin!!,
        test.exec?.address!!,
        test.exec?.value!!,
        test.exec?.code!!,
        test.exec?.data!!,
        test.exec?.gas!!,
        test.exec?.gasPrice!!,
        test.env?.currentCoinbase!!,
        test.env?.currentNumber!!.toLong(),
        test.env?.currentTimestamp!!.toLong(),
        test.env?.currentGasLimit!!.toLong(),
        test.env?.currentDifficulty!!
      )
      if (test.post == null) {
        assertNotEquals(EVMExecutionStatusCode.SUCCESS, result.statusCode)
        if (testName.contains("JumpDest", true) ||
          testName.contains("OutsideBoundary", true) ||
          testName.contains("outOfBoundary", true) ||
          testName.startsWith("jumpiToUintmaxPlus1") ||
          testName.startsWith("jumpToUintmaxPlus1") ||
          testName.startsWith("DynamicJumpi0") ||
          testName.startsWith("DynamicJumpJD_DependsOnJumps0") ||
          testName.startsWith("jumpHigh") ||
          testName.startsWith("bad_indirect_jump2") ||
          testName.startsWith("DynamicJumpPathologicalTest1") ||
          testName.startsWith("jumpToUint64maxPlus1") ||
          testName.startsWith("jumpiToUint64maxPlus1") ||
          testName.startsWith("jumpi0") ||
          testName.startsWith("DynamicJumpPathologicalTest3") ||
          testName.startsWith("DynamicJumpPathologicalTest2") ||
          testName.startsWith("jump1") ||
          testName.startsWith("bad_indirect_jump1") ||
          testName.startsWith("BlockNumberDynamicJumpi0") ||
          testName.startsWith("gasOverFlow") ||
          testName.startsWith("DynamicJump1") ||
          testName.startsWith("BlockNumberDynamicJump1") ||
          testName.startsWith("JDfromStorageDynamicJump1") ||
          testName.startsWith("JDfromStorageDynamicJumpi0")
        ) {
          assertEquals(EVMExecutionStatusCode.BAD_JUMP_DESTINATION, result.statusCode)
        } else if (testName.contains("underflow", true) ||
          testName.startsWith("swap2error") ||
          testName.startsWith("dup2error") ||
          testName.startsWith("DynamicJump_valueUnderflow") ||
          testName.startsWith("pop1") ||
          testName.startsWith("jumpOntoJump") ||
          testName.startsWith("swapAt52becameMstore") ||
          testName.startsWith("stack_loop") ||
          testName.startsWith("201503110206PYTHON") ||
          testName.startsWith("201503112218PYTHON") ||
          testName.startsWith("201503110219PYTHON") ||
          testName.startsWith("201503102320PYTHON")
        ) {
          assertEquals(EVMExecutionStatusCode.STACK_UNDERFLOW, result.statusCode)
        } else if (testName.contains("outofgas", true) ||
          testName.contains("TooHigh", true) ||
          testName.contains("MemExp", true) ||
          testName.contains("return1", true) ||
          testName.startsWith("sha3_bigOffset") ||
          testName.startsWith("sha3_3") ||
          testName.startsWith("sha3_4") ||
          testName.startsWith("sha3_5") ||
          testName.startsWith("sha3_6") ||
          testName.startsWith("sha3_bigSize") ||
          testName.startsWith("ackermann33")
        ) {
          assertEquals(EVMExecutionStatusCode.OUT_OF_GAS, result.statusCode)
        } else if (testName.contains("stacklimit", true)) {
          assertEquals(EVMExecutionStatusCode.STACK_OVERFLOW, result.statusCode)
        } else {
          println(result.statusCode)
          TODO()
        }
      } else {
        assertEquals(EVMExecutionStatusCode.SUCCESS, result.statusCode)

        test.post!!.forEach { address, state ->
          runBlocking {
            assertTrue(
              repository.accountsExists(address) ||
                (result.hostContext as TransactionalEVMHostContext).getAccountChanges().containsKey(address)
            )
            val accountState = repository.getAccount(address)
            val balance = accountState?.balance?.add(
              result.changes.getBalanceChanges().get(address) ?: Wei.valueOf(0)
            ) ?: Wei.valueOf(0)
            assertEquals(state.balance, balance)
            assertEquals(state.nonce, accountState!!.nonce)

            for (stored in state.storage!!) {
              val changed = result.hostContext.getStorage(address, stored.key)
              assertEquals(stored.value, changed)
            }
          }
        }
        test.logs?.let {
          val logsTree = MerklePatriciaTrie.storingBytes()
          (result.hostContext as TransactionalEVMHostContext).getLogs().forEach {
            runBlocking {
              logsTree.put(Hash.hash(it.toBytes()), it.toBytes())
            }
          }
        }

        // assertEquals(test.gas, result.gasManager.gasLeft())
        if (test.out?.isEmpty == true) {
          assertTrue(result.state.output == null || result.state.output?.isEmpty ?: false)
        } else {
          assertEquals(
            test.out?.let { if (it.size() < 32) Bytes32.rightPad(it) else it },
            result.state.output?.let { if (it.size() < 32) Bytes32.rightPad(it) else it }
          )
        }
      }
    } finally {
      vm.stop()
    }
  }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class Env(
  var currentCoinbase: Address? = null,
  var currentDifficulty: UInt256? = null,
  var currentGasLimit: UInt256? = null,
  var currentNumber: UInt256? = null,
  var currentTimestamp: UInt256? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Exec(
  var address: Address? = null,
  var caller: Address? = null,
  var code: Bytes? = null,
  var data: Bytes? = null,
  var gas: Gas? = null,
  var gasPrice: Wei? = null,
  var origin: Address? = null,
  var value: Bytes? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonAccountState(
  var balance: Wei? = null,
  var code: Bytes? = null,
  var nonce: UInt256? = null,
  var storage: Map<UInt256, UInt256>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonReferenceTest(
  var env: Env? = null,
  var exec: Exec? = null,
  var gas: Gas? = null,
  var logs: Bytes? = null,
  var out: Bytes? = null,
  var post: Map<Address, JsonAccountState>? = null,
  var pre: Map<Address, JsonAccountState>? = null,
)
