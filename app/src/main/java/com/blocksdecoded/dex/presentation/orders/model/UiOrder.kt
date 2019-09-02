package com.blocksdecoded.dex.presentation.orders.model

import com.blocksdecoded.dex.core.manager.ICoinManager
import com.blocksdecoded.dex.core.model.Coin
import com.blocksdecoded.dex.core.model.CoinType
import com.blocksdecoded.dex.core.rates.RatesConverter
import com.blocksdecoded.dex.utils.TimeUtils
import com.blocksdecoded.zrxkit.model.EAssetProxyId
import com.blocksdecoded.zrxkit.model.IOrder
import com.blocksdecoded.zrxkit.model.OrderInfo
import java.math.BigDecimal

data class UiOrder(
        val makerCoin: Coin,
        val takerCoin: Coin,
        val price: BigDecimal,
        val makerAmount: BigDecimal,
        val takerAmount: BigDecimal,
        val makerFiatAmount: BigDecimal,
        val takerFiatAmount: BigDecimal,
        val expireDate: String,
        val side: EOrderSide,
        val isMine: Boolean,
        val status: String,
        val filledAmount: BigDecimal
){
    companion object {
        fun fromOrder(
            coinManager: ICoinManager,
            ratesConverter: RatesConverter,
            order: IOrder,
            side: EOrderSide,
            orderInfo: OrderInfo? = null,
            isMine: Boolean = false
        ): UiOrder {
            val makerCoin = coinManager.getErcCoinForAddress(EAssetProxyId.ERC20.decode(order.makerAssetData))!!
            val takerCoin = coinManager.getErcCoinForAddress(EAssetProxyId.ERC20.decode(order.takerAssetData))!!

            val makerAmount = order.makerAssetAmount.toBigDecimal()
                .movePointLeft((makerCoin.type as CoinType.Erc20).decimal)
                .stripTrailingZeros()

            val takerAmount = order.takerAssetAmount.toBigDecimal()
                .movePointLeft((takerCoin.type as CoinType.Erc20).decimal)
                .stripTrailingZeros()

            val filledAmount = orderInfo?.orderTakerAssetFilledAmount?.toBigDecimal()
                ?.movePointLeft(takerCoin.type.decimal)
                ?.stripTrailingZeros() ?: BigDecimal.ZERO

            val price = if (side == EOrderSide.BUY)
                makerAmount.toDouble().div(takerAmount.toDouble())
            else
                takerAmount.toDouble().div(makerAmount.toDouble())
            
            return UiOrder(
                makerCoin,
                takerCoin,
                price.toBigDecimal(),
                makerAmount,
                takerAmount,
                ratesConverter.getCoinsPrice(makerCoin.code, makerAmount),
                ratesConverter.getCoinsPrice(takerCoin.code, takerAmount),
                TimeUtils.timestampToDisplay(order.expirationTimeSeconds.toLong()),
                side,
                isMine,
                orderInfo?.orderStatus ?: "unknown",
                filledAmount
            )
        }
    }
}