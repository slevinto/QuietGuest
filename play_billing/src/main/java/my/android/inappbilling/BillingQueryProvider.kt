package my.android.inappbilling

import my.android.inappbilling.enums.BillingOK

interface BillingQueryProvider {
    fun onBillingOk(skuType: BillingOK): BillingQueryProvider
    fun onQueryResult(result: (responseCode: BillingResponse, skuDetailsList: MutableList<AugmentedSkuDetails>?) -> Unit)
    fun onPurchasesQueryResult(purchaseList: (List<AugmentedPurchase>) -> Unit): BillingPurchaseProvider
}