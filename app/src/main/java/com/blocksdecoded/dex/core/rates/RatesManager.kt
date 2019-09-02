package com.blocksdecoded.dex.core.rates

import android.annotation.SuppressLint
import com.blocksdecoded.dex.core.manager.ICoinManager
import com.blocksdecoded.dex.core.model.Rate
import com.blocksdecoded.dex.core.rates.bootstrap.IBootstrapClient
import com.blocksdecoded.dex.core.model.Market
import com.blocksdecoded.dex.core.rates.remote.IRatesApiClient
import com.blocksdecoded.dex.core.rates.remote.IRatesClientConfig
import com.blocksdecoded.dex.utils.Logger
import com.blocksdecoded.dex.utils.ioSubscribe
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.BehaviorSubject

class RatesManager(
    private val coinManager: ICoinManager,
    private val marketsStorage: IMarketsStorage,
    private val ratesStorage: IRatesStorage,
    private val bootstrapClient: IBootstrapClient,
    private val rateClient: IRatesApiClient,
    private val rateClientConfig: IRatesClientConfig
): IRatesManager {
    private val disposables = CompositeDisposable()

    override val marketsUpdateSubject: BehaviorSubject<Unit> = BehaviorSubject.create()
    override val marketsStateSubject: BehaviorSubject<MarketState> = BehaviorSubject.create()

    private var bootstrapSynced = false
    private var cachedRates = listOf<Market>()

    init {
        marketsStateSubject.onNext(MarketState.SYNCING)

        initialStorageFetch()

        initialBootstrapConfig()
    }

    //region Private

    private fun initialBootstrapConfig() {
        bootstrapClient.getConfigs().ioSubscribe(disposables, {
            bootstrapSynced = true
            rateClientConfig.ipfsUrl = it.servers.first()
            rateClient.init(rateClientConfig)
            fetchRates()
        }, {
            Logger.e(it)
            fetchRates()
        })
    }

    @SuppressLint("CheckResult")
    private fun initialStorageFetch() {
        marketsStorage.getAllMarkets().ioSubscribe(disposables, {
            cachedRates = it
            marketsUpdateSubject.onNext(Unit)
        })
    }

    private fun fetchRates() {
        rateClient.getRates().ioSubscribe(disposables, {
                cachedRates = it.data.markets
                marketsStorage.save(*cachedRates.toTypedArray())
                marketsStateSubject.onNext(MarketState.SYNCED)
                marketsUpdateSubject.onNext(Unit)
            }, {
                marketsStateSubject.onNext(MarketState.FAILED)
                Logger.e(it)
            })
    }

    //endregion

    //region Public

    //region Rates

    override fun getRate(coinCode: String, timeStamp: Long): Rate? =
        ratesStorage.getRate(coinManager.cleanCoinCode(coinCode), timeStamp)

    override fun getRateSingle(coinCode: String, timeStamp: Long): Single<Rate> {
        val cleanCoinCode = coinManager.cleanCoinCode(coinCode)

        return ratesStorage.getRateSingle(cleanCoinCode, timeStamp)
            .onErrorResumeNext(
                rateClient.getHistoricalRate(cleanCoinCode, timeStamp).map {
                    val rate = Rate(cleanCoinCode, timeStamp, it)
                    ratesStorage.save(rate)
                    rate
                }
            )
    }

    //endregion

    //region Markets

    override fun getMarkets(coinCodes: List<String>): List<Market> =
        cachedRates.filter {
            coinCodes.contains(it.coinCode) ||
                 coinCodes.indexOfFirst { symbol -> symbol.contains(it.coinCode) } >= 0
        }

    override fun getMarket(coinCode: String): Market {
        val cleanCoinCode = coinManager.cleanCoinCode(coinCode)
        return cachedRates.firstOrNull {
            it.coinCode == cleanCoinCode || it.coinCode.contains(cleanCoinCode, true)
        } ?: Market(coinCode)
    }

    override fun refresh() {
        marketsStateSubject.value?.let {
            if (it != MarketState.SYNCING) {
                marketsStateSubject.onNext(MarketState.SYNCING)

                if (bootstrapSynced) {
                    fetchRates()
                } else {
                    initialBootstrapConfig()
                }
            }
        }
    }

    //endregion

    override fun stop() {
        disposables.dispose()
    }

    override fun clear() {
        marketsStorage.deleteAll()
        stop()
    }

    //endregion
}