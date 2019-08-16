package com.blocksdecoded.dex.presentation.markets.recycler

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.blocksdecoded.dex.R
import com.blocksdecoded.dex.core.model.Market
import com.blocksdecoded.dex.presentation.widgets.CoinIconImage
import com.blocksdecoded.dex.presentation.widgets.MarketChart
import com.blocksdecoded.dex.utils.Logger
import com.blocksdecoded.dex.utils.ui.toFiatDisplayFormat
import com.blocksdecoded.dex.utils.visible
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.ChartData
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

class MarketViewHolder(
	view: View,
	private val listener: Listener
): RecyclerView.ViewHolder(view) {

	private val coinIcon: CoinIconImage = itemView.findViewById(R.id.market_coin_icon)
	private val nameTxt: TextView = itemView.findViewById(R.id.market_name)
	private val priceTxt: TextView = itemView.findViewById(R.id.market_price)
	private val codeTxt: TextView = itemView.findViewById(R.id.market_code)
	private val changeImg: ImageView = itemView.findViewById(R.id.market_change_img)
	private val chart: MarketChart = itemView.findViewById(R.id.market_chart)
	
	init {
		itemView.setOnClickListener { listener.onClick(adapterPosition) }
	}
	
	fun onBind(market: Market) {
		val color = if (market.rate.priceChange >= 0) {
			R.color.green
		} else {
			R.color.red
		}
		
		nameTxt.text = market.coin.title
		codeTxt.text = market.coin.code
		coinIcon.bind(market.coin.code)
		priceTxt.text = "$${market.rate.price.toFiatDisplayFormat()}"
		
		chart.displayData(market.rate.history, color, R.color.transparent)
	}
	
	interface Listener {
		fun onClick(position: Int)
	}
}