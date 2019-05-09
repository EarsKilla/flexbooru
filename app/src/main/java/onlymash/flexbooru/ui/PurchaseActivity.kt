/*
 * Copyright (C) 2019. by onlymash <im@fiepi.me>, All rights reserved
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package onlymash.flexbooru.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.android.billingclient.api.*
import kotlinx.android.synthetic.main.activity_purchase.*
import kotlinx.android.synthetic.main.toolbar.*
import onlymash.flexbooru.App
import onlymash.flexbooru.R
import onlymash.flexbooru.Settings
import onlymash.flexbooru.api.OrderApi

class PurchaseActivity : BaseActivity(), PurchasesUpdatedListener {

    companion object {
        const val SKU = "flexbooru_pro"
    }
    private var billingClient: BillingClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_purchase)
        toolbar.setTitle(R.string.purchase_title)
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
        if (Settings.instance().isGoogleSign) {
            pay_alipay.visibility = View.GONE
            pay_redeem_code.visibility = View.GONE
            billingClient = BillingClient
                .newBuilder(this)
                .setListener(this)
                .build()
            billingClient?.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult?) {

                }

                override fun onBillingServiceDisconnected() {

                }
            })
            pay_google_play.setOnClickListener {
                billingClient?.let { client ->
                    if (client.isReady) {
                        val params = SkuDetailsParams
                            .newBuilder()
                            .setSkusList(listOf(SKU))
                            .setType(BillingClient.SkuType.INAPP)
                            .build()
                        client.querySkuDetailsAsync(params) { billingResult, skuDetailsList ->
                            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && skuDetailsList != null) {
                                val index = skuDetailsList.indexOfFirst {
                                    it.sku == SKU
                                }
                                if (index >= 0) {
                                    val billingFlowParams = BillingFlowParams
                                        .newBuilder()
                                        .setSkuDetails(skuDetailsList[index])
                                        .build()
                                    val result = client.launchBillingFlow(this, billingFlowParams)
                                    if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                                        Settings.instance().isOrderSuccess = true
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            pay_google_play.visibility = View.GONE
            pay_alipay.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle(R.string.purchase_pay_alipay_title)
                    .setMessage(R.string.purchase_pay_alipay_info)
                    .setPositiveButton(R.string.purchase_pay_alipay_dialog_positive) { _, _ ->
                        App.app.clipboard.primaryClip = ClipData.newPlainText("alipay", "im@fiepi.com")
                        val alipayPackageName = "com.eg.android.AlipayGphone"
                        try {
                            val intent = packageManager.getLaunchIntentForPackage(alipayPackageName)
                            if (intent != null) {
                                startActivity(intent)
                            }
                        } catch (_: PackageManager.NameNotFoundException) {

                        } catch (_: ActivityNotFoundException) {

                        }
                    }
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .show()
            }
            pay_redeem_code.setOnClickListener {
                val padding = resources.getDimensionPixelSize(R.dimen.spacing_mlarge)
                val layout = FrameLayout(this@PurchaseActivity).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    setPadding(padding, padding, padding, 0)
                }
                val editText = EditText(this@PurchaseActivity)
                editText.setHint(R.string.purchase_pay_order_code_hint)
                layout.addView(editText)
                AlertDialog.Builder(this@PurchaseActivity)
                    .setTitle(R.string.purchase_pay_order_code)
                    .setView(layout)
                    .setPositiveButton(R.string.purchase_pay_order_code_summit) { _, _ ->
                        val orderId = (editText.text ?: "").toString().trim()
                        if (orderId.isNotEmpty()) {
                            OrderApi.orderRegister(orderId, Settings.instance().orderDeviceId) { success ->
                                if (!isFinishing) {
                                    if (success) {
                                        Toast.makeText(
                                            this@PurchaseActivity,
                                            getString(R.string.purchase_pay_order_code_summit_success),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            this@PurchaseActivity,
                                            getString(R.string.purchase_pay_order_code_summit_failed),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        }
                    }
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .create()
                    .show()
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult?, purchases: MutableList<Purchase>?) {
        val responseCode = billingResult?.responseCode ?: return
        if (responseCode == BillingClient.BillingResponseCode.OK ||
            responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            Settings.instance().isOrderSuccess = true
            if (purchases == null) return
            val index = purchases.indexOfFirst { it.sku == SKU && it.purchaseState == Purchase.PurchaseState.PURCHASED }
            if (index >= 0) {
                val purchase = purchases[index]
                Settings.instance().orderId = purchase.orderId
                Settings.instance().orderTime = purchase.purchaseTime
                Settings.instance().orderToken = purchase.purchaseToken
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        billingClient?.apply {
            if (isReady) endConnection()
        }
    }
}
