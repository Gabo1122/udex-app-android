package com.fridaytech.dex.data.zrx

import android.os.Handler
import android.os.HandlerThread
import com.fridaytech.dex.data.manager.ICoinManager
import com.fridaytech.dex.data.manager.IEthereumKitManager
import com.fridaytech.dex.data.manager.auth.IAuthManager
import com.fridaytech.dex.data.zrx.adapter.AllowanceChecker
import com.fridaytech.dex.data.zrx.adapter.BaseRelayerAdapter
import com.fridaytech.dex.data.zrx.adapter.ExchangeInteractor
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject

class RelayerAdapterManager(
    private val coinManager: ICoinManager,
    private val ethereumKitManager: IEthereumKitManager,
    private val zrxKitManager: IZrxKitManager,
    private val authManager: IAuthManager
) : IRelayerAdapterManager, HandlerThread("R") {

    private val handler: Handler
    private val disposables = CompositeDisposable()

    override val refreshInterval = 10L

    override var mainRelayer: IRelayerAdapter? = null
    override val mainRelayerUpdatedSignal: BehaviorSubject<Unit> = BehaviorSubject.create()

    init {
        start()

        handler = Handler(looper)

        disposables.add(authManager.authDataSubject
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .subscribe { initRelayer() })
    }

    override fun refresh() {
    }

    override fun initRelayer() {
        handler.post {
            val authData = authManager.authData
            if (authData == null) {
                clearRelayers()
                mainRelayerUpdatedSignal.onNext(Unit)
            } else {
                authManager.authData?.let { auth ->
                    val ethereumKit = ethereumKitManager.ethereumKit(auth)
                    val zrxKit = zrxKitManager.zrxKit()
                    val exchangeWrapper = zrxKit.getExchangeInstance()
                    val exchangeInteractor = ExchangeInteractor(
                        coinManager,
                        ethereumKit,
                        zrxKit,
                        exchangeWrapper,
                        AllowanceChecker(ethereumKit, zrxKit)
                    )

                    mainRelayer = BaseRelayerAdapter(
                        coinManager,
                        ethereumKit,
                        exchangeInteractor,
                        zrxKit,
                        refreshInterval,
                        0
                    )

                    mainRelayerUpdatedSignal.onNext(Unit)
                }
            }
        }
    }

    override fun clearRelayers() {
        mainRelayer?.let {
            ethereumKitManager.unlink()
            mainRelayer = null
        }
    }
}
