package com.fridaytech.dex.core.model

import java.math.BigDecimal
import java.util.*

data class TransactionRecord(
    val transactionHash: String,
    val transactionIndex: Int,
    val interTransactionIndex: Int,
    val blockHeight: Long?,
    val amount: BigDecimal,
    val fee: BigDecimal? = null,
    val timestamp: Long,
    val from: List<TransactionAddress>,
    val to: List<TransactionAddress>
) : Comparable<TransactionRecord> {

    override fun compareTo(other: TransactionRecord): Int {
        return when {
            timestamp != other.timestamp -> timestamp.compareTo(other.timestamp)
            transactionIndex != other.transactionIndex -> transactionIndex.compareTo(other.transactionIndex)
            else -> interTransactionIndex.compareTo(other.interTransactionIndex)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other is TransactionRecord) {
            return transactionHash == other.transactionHash && interTransactionIndex == other.interTransactionIndex
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return Objects.hash(transactionHash, interTransactionIndex)
    }
}

class TransactionAddress(val address: String, val mine: Boolean)
