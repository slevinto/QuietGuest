package my.android.inappbilling

interface BillingPurchaseProvider {
    //fun onLaunchBillingFlow(skuType: BillingRepo.BillingOK, data:SkuDetails): BillingPurchaseProvider
    /*fun onQueryResult(result: (responseCode: Int, skuDetailsList: MutableList<SkuDetails>?) -> Unit)
    fun startDataSourceConnections(): BillingPurchaseProvider*/
    fun onPurchaseUpdated(result: (responseCode: BillingResponse, purchaseList: MutableList<AugmentedPurchase>?) -> Unit): BillingPurchaseProvider

    fun launchBillingFlow(skuDetails: AugmentedSkuDetails): BillingPurchaseProvider

    fun launchBillingFlow(skuDetails: AugmentedSkuDetails, result:(responseCode: BillingResponse, purchaseList: MutableList<AugmentedPurchase>?) -> Unit) : BillingPurchaseProvider
    fun consumePurchase(augmentedPurchase: AugmentedPurchase, listener: (responseCode: BillingResponse, purchaseToken: String?) -> Unit): BillingPurchaseProvider
}