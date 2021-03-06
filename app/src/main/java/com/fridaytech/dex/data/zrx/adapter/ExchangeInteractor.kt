package com.fridaytech.dex.data.zrx.adapter

import com.fridaytech.dex.core.CancelOrderException
import com.fridaytech.dex.core.CreateOrderException
import com.fridaytech.dex.core.model.CoinType
import com.fridaytech.dex.data.manager.ICoinManager
import com.fridaytech.dex.data.zrx.IAllowanceChecker
import com.fridaytech.dex.data.zrx.IExchangeInteractor
import com.fridaytech.dex.data.zrx.model.CreateOrderData
import com.fridaytech.dex.data.zrx.model.FillOrderData
import com.fridaytech.dex.presentation.orders.model.EOrderSide
import com.fridaytech.zrxkit.ZrxKit
import com.fridaytech.zrxkit.contracts.IZrxExchange
import com.fridaytech.zrxkit.model.Order
import com.fridaytech.zrxkit.model.OrderInfo
import com.fridaytech.zrxkit.model.SignedOrder
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.reactivex.Flowable
import java.math.BigInteger
import java.util.*

class ExchangeInteractor(
    private val coinManager: ICoinManager,
    private val ethereumKit: EthereumKit,
    private val zrxKit: ZrxKit,
    private val exchangeWrapper: IZrxExchange,
    private val allowanceChecker: IAllowanceChecker
) : IExchangeInteractor {

    private val orderValidTime = 60 * 60 * 24 * 1 // 1 day

    //region Private

    private fun postOrderToRelayer(
        feeRecipient: String,
        makeAsset: String,
        makeAmount: BigInteger,
        takeAsset: String,
        takeAmount: BigInteger,
        side: EOrderSide
    ): Flowable<SignedOrder> {
        val expirationTime = ((Date().time / 1000) + orderValidTime).toString() // Order valid for 1 day

        val makerAsset = when (side) {
            EOrderSide.BUY -> takeAsset
            else -> makeAsset
        }

        val takerAsset = when (side) {
            EOrderSide.BUY -> makeAsset
            else -> takeAsset
        }

        val order = Order(
            makerAddress = ethereumKit.receiveAddress.toLowerCase(),
            exchangeAddress = exchangeWrapper.address,
            makerAssetData = makerAsset,
            takerAssetData = takerAsset,
            makerAssetAmount = makeAmount.toString(),
            takerAssetAmount = takeAmount.toString(),
            expirationTimeSeconds = expirationTime,
            senderAddress = "0x0000000000000000000000000000000000000000",
            takerAddress = "0x0000000000000000000000000000000000000000",
            makerFee = "0",
            takerFee = "0",
            feeRecipientAddress = feeRecipient,
            salt = Date().time.toString()
        )

        val signedOrder = zrxKit.signOrder(order)

        return if (signedOrder != null) {
            zrxKit.relayerManager.postOrder(0, signedOrder)
                .map { signedOrder }
        } else {
            Flowable.error(CreateOrderException())
        }
    }

    //endregion

    //region Public

    override fun createOrder(feeRecipient: String, createData: CreateOrderData): Flowable<SignedOrder> {
        val baseCoin = coinManager.getCoin(createData.coinPair.first).type as CoinType.Erc20
        val quoteCoin = coinManager.getCoin(createData.coinPair.second).type as CoinType.Erc20

        val baseAsset = ZrxKit.assetItemForAddress(baseCoin.address)
        val quoteAsset = ZrxKit.assetItemForAddress(quoteCoin.address)

        val makerAmount = createData.amount.movePointRight(
            if (createData.side == EOrderSide.BUY) quoteCoin.decimal else baseCoin.decimal
        ).stripTrailingZeros().toBigInteger()

        val takerAmount = createData.amount.multiply(createData.price).movePointRight(
            if (createData.side == EOrderSide.BUY) baseCoin.decimal else quoteCoin.decimal
        ).stripTrailingZeros().toBigInteger()

        return allowanceChecker.checkAndUnlockAssetPairForPost(baseAsset to quoteAsset, createData.side)
            .flatMap { postOrderToRelayer(feeRecipient, baseAsset.assetData, makerAmount, quoteAsset.assetData, takerAmount, createData.side) }
    }

    override fun cancelOrder(order: SignedOrder): Flowable<String> =
        if (order.makerAddress.equals(ethereumKit.receiveAddress, true)) {
            exchangeWrapper.cancelOrder(order)
        } else {
            Flowable.error(
                CancelOrderException(
                    order.makerAddress,
                    ethereumKit.receiveAddress
                )
            )
        }

    override fun batchCancelOrders(orders: List<SignedOrder>): Flowable<String> =
        exchangeWrapper.batchCancelOrders(orders)

    override fun fill(orders: List<SignedOrder>, fillData: FillOrderData): Flowable<String> {
        val baseCoin = coinManager.getCoin(fillData.coinPair.first).type as CoinType.Erc20
        val quoteCoin = coinManager.getCoin(fillData.coinPair.second).type as CoinType.Erc20

        val calcAmount = fillData.amount.movePointRight(
            if (fillData.side == EOrderSide.BUY) quoteCoin.decimal else baseCoin.decimal
        )

        return allowanceChecker.checkAndUnlockPairForFill(baseCoin.address to quoteCoin.address, fillData.side)
            .flatMap { exchangeWrapper.marketBuyOrders(orders, calcAmount.toBigInteger()) }
    }

    override fun ordersInfo(orders: List<SignedOrder>): Flowable<List<OrderInfo>> =
        exchangeWrapper.ordersInfo(orders)

    //endregion
}
