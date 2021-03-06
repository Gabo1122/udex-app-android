package com.fridaytech.dex.presentation.qrscanner

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import com.fridaytech.dex.R
import com.google.zxing.integration.android.IntentIntegrator
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView

class QRScannerActivity : AppCompatActivity() {

    private var capture: CaptureManager? = null
    private var barcodeScannerView: DecoratedBarcodeView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_qr_scanner)

        barcodeScannerView = findViewById(R.id.zxing_barcode_scanner)

        capture = CaptureManager(this, barcodeScannerView)
        capture?.initializeFromIntent(intent, savedInstanceState)
        capture?.decode()
    }

    override fun onResume() {
        super.onResume()
        capture?.onResume()
    }

    override fun onPause() {
        super.onPause()
        capture?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        capture?.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        capture?.onSaveInstanceState(outState)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        capture?.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return barcodeScannerView?.onKeyDown(keyCode, event) ?: false || super.onKeyDown(keyCode, event)
    }

    companion object {
        fun start(activity: Activity) {
            val intentIntegrator = IntentIntegrator(activity)
            intentIntegrator.captureActivity = QRScannerActivity::class.java
            intentIntegrator.setOrientationLocked(true)
            intentIntegrator.setPrompt("")
            intentIntegrator.setBeepEnabled(false)
            intentIntegrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            intentIntegrator.initiateScan()
        }
    }
}
