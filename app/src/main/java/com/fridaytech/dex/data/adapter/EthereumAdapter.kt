package com.fridaytech.dex.data.adapter

import android.content.Context
import com.fridaytech.dex.core.model.Coin
import com.fridaytech.dex.core.model.TransactionAddress
import com.fridaytech.dex.core.model.TransactionRecord
import com.fridaytech.dex.data.manager.fee.IFeeRateProvider
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.models.TransactionInfo
import io.reactivex.Flowable
import io.reactivex.Single
import java.math.BigDecimal

class EthereumAdapter(
    coin: Coin,
    kit: EthereumKit,
    feeRateProvider: IFeeRateProvider
) : EthereumBaseAdapter(coin, kit, feeRateProvider, 18) {

    override val state: AdapterState
        get() = when (ethereumKit.syncState) {
            is EthereumKit.SyncState.Synced -> AdapterState.Synced
            is EthereumKit.SyncState.NotSynced -> AdapterState.NotSynced
            is EthereumKit.SyncState.Syncing -> AdapterState.Syncing(
                50,
                null
            )
        }

    override val stateUpdatedFlowable: Flowable<Unit>
        get() = ethereumKit.syncStateFlowable.map { Unit }

    override val balance: BigDecimal
        get() = balanceInBigDecimal(ethereumKit.balance, decimal)

    override val balanceUpdatedFlowable: Flowable<Unit>
        get() = ethereumKit.balanceFlowable.map { Unit }

    override fun getTransactions(from: Pair<String, Int>?, limit: Int): Single<List<TransactionRecord>> {
        return ethereumKit.transactions(from?.first, limit).map {
            it.map { tx -> transactionRecord(tx) }
        }
    }

    override val transactionRecordsFlowable: Flowable<List<TransactionRecord>>
        get() = ethereumKit.transactionsFlowable.map { it.map { tx -> transactionRecord(tx) } }

    override fun sendSingle(address: String, amount: String, gasPrice: Long): Single<Unit> {
        return ethereumKit.send(address, amount, gasPrice).map { Unit }
    }

    override fun fee(value: BigDecimal, address: String?, feePriority: FeeRatePriority): BigDecimal {
        return ethereumKit.fee(feeRateProvider.ethereumGasPrice(feePriority)).movePointLeft(18)
    }

    override fun availableBalance(address: String?, feePriority: FeeRatePriority): BigDecimal {
        return BigDecimal.ZERO.max(balance - fee(balance, address, feePriority))
    }

    override fun validate(amount: BigDecimal, address: String?, feePriority: FeeRatePriority): List<SendStateError> {
        val errors = mutableListOf<SendStateError>()
        if (amount > availableBalance(address, feePriority)) {
            errors.add(SendStateError.InsufficientAmount)
        }
        return errors
    }

    private fun transactionRecord(transaction: TransactionInfo): TransactionRecord {
        val mineAddress = ethereumKit.receiveAddress

        val fromAddressHex = transaction.from
        val from = TransactionAddress(
            fromAddressHex,
            fromAddressHex == mineAddress
        )

        val toAddressHex = transaction.to
        val to = TransactionAddress(
            toAddressHex,
            toAddressHex == mineAddress
        )

        var amount: BigDecimal

        transaction.value.toBigDecimal().let {
            amount = it.movePointLeft(decimal)
            if (from.mine) {
                amount = -amount
            }
        }

        val fee = transaction.gasUsed?.toBigDecimal()?.multiply(transaction.gasPrice.toBigDecimal())?.movePointLeft(decimal)

        return TransactionRecord(
            transactionHash = transaction.hash,
            transactionIndex = transaction.transactionIndex ?: 0,
            interTransactionIndex = 0,
            blockHeight = transaction.blockNumber,
            amount = amount,
            fee = fee,
            timestamp = transaction.timestamp,
            from = listOf(from),
            to = listOf(to)
        )
    }

    companion object {
        fun clear(context: Context) {
            EthereumKit.clear(context)
        }
    }
}
