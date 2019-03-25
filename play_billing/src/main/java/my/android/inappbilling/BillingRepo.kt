package my.android.inappbilling

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.BillingResponse.BILLING_UNAVAILABLE
import com.android.billingclient.api.BillingClient.BillingResponse.ERROR
import my.android.inappbilling.enums.BillingOK
import my.android.inappbilling.enums.ProductCategory
import my.android.inappbilling.utils.RxUtils
import java.util.*

class BillingRepo private constructor(private val application: Application) :
        PurchasesUpdatedListener, BillingClientStateListener,
        ConsumeResponseListener, SkuDetailsResponseListener, BillingQueryProvider, BillingPurchaseProvider {

    /**
     * The Play [BillingClient] is the most reliable and primary source of truth for all purchases
     * made through the Play Store. The Play Store takes security precautions from guarding the data.
     * Also the data is available offline from most cases, which means the app incurs no network
     * charges for checking for purchases using the Play [BillingClient]. The offline bit is
     * because the Play Store caches every purchase the user owns, from an
     * [eventually consistent manner](https://developer.android.com/google/play/billing/billing_library_overview#Keep-up-to-date).
     * This is the only billing client an app is actually required to have on Android. The other
     * two are optional.
     *
     * ASIDE. Notice that the connection to [playStoreBillingClient] is created using the
     * applicationContext. This means the instance is not [Activity]-specific. And since it's also
     * not expensive, it can remain open for the life of the entire [Application]. So whether it is
     * (re)created for each [Activity] or [Fragment] or is kept open for the life of the application
     * is a matter of choice.
     */
    private lateinit var playStoreBillingClient: BillingClient
    lateinit var activity: Activity
    val skuItemsMap: HashMap<String, List<String>> = hashMapOf()

    private lateinit var billingClientConnectionListener: (billingConnectionState: Int) -> Unit
    private lateinit var billingOk: BillingOK
    private var billingError: (() -> Int)? = null
    private lateinit var purchases: (List<AugmentedPurchase>) -> Unit
    private lateinit var skuDetailsResponseListener: (responseCode: BillingResponse, skuDetailsList: MutableList<AugmentedSkuDetails>?) -> Unit
    private lateinit var purchaseUpdateListener: (responseCode: BillingResponse, purchases: MutableList<AugmentedPurchase>?) -> Unit
    private lateinit var purchaseConsumedListener: (responseCode: BillingResponse, purchaseToken: String?) -> Unit
    private var queryCount:Int = 0
    private var skuDetailsGroupList:MutableList<AugmentedSkuDetails> = mutableListOf()

    fun from(activity: Activity): BillingRepo {
        this.activity = activity
        return this
    }

    fun items(skuType: String, skuList: List<String>): BillingRepo {
        skuItemsMap[skuType] = skuList
        return this
    }

    override fun onBillingOk(skuType: BillingOK): BillingQueryProvider {
        billingOk = skuType
        return this
    }

    private fun onBillingError(responseCode: Int, errorText: String) {
        billingError = {
            Log.e(javaClass.name, errorText)
            responseCode
        }
        billingError?.invoke()
    }

    private fun onBillingOk() {
        when (billingOk) {
            BillingOK.QUERY_INAPP -> {
                Log.d(javaClass.name, " Inapp query")
                querySkuDetailsAsync(ProductCategory.INAPP.value)
            }
            BillingOK.QUERY_SUBSCRIPTIONS -> {
                Log.d(javaClass.name, " Subs query")
                querySkuDetailsAsync(ProductCategory.SUBS.value)
            }
            BillingOK.QUERY_BOTH -> {
                Log.d(javaClass.name, " Inapp & Subs query")
                querySkuDetailsAsync(ProductCategory.INAPP.value)
                querySkuDetailsAsync(ProductCategory.SUBS.value)
            }
            BillingOK.QUERY_PURCHASES -> {
                Log.d(javaClass.name, " purchases query")
                queryPurchasesSync()
            }
            BillingOK.CONSUME_INAPP_PURCHASE -> {
                Log.d(javaClass.name, " consume inapp purchases")

            }
        }
    }

    override fun onQueryResult(result: (responseCode: BillingResponse, skuDetailsList: MutableList<AugmentedSkuDetails>?) -> Unit) {
        skuDetailsResponseListener = result
    }

    override fun onPurchasesQueryResult(purchaseList: (List<AugmentedPurchase>) -> Unit): BillingPurchaseProvider {
        purchases = purchaseList
        return this
    }

    override fun onPurchaseUpdated(result: (responseCode: BillingResponse, purchaseList: MutableList<AugmentedPurchase>?) -> Unit): BillingPurchaseProvider {
        purchaseUpdateListener = result
        return this
    }

    //END list of each distinct item user may own (i.e. entitlements)

    /**
     * Correlated data sources necessarily belong inside a repository module so that --
     * as mentioned above -- the rest of the app can have effortless access to the data it needs.
     * Still, it may be effective to track the opening (and sometimes closing) of data source
     * connections based on lifecycle events. One convenient way of doing that is by calling this
     * [startDataSourceConnections] when the [BillingViewModel] is instantiated and
     * [endDataSourceConnections] inside [ViewModel.onCleared]
     */
    fun startDataSourceConnections(): BillingRepo {
        Log.d(javaClass.name, "startDataSourceConnections")
        instantiateAndConnectToPlayBillingService()
        return this
        //secureServerBillingClient = BillingWebservice.create()
        //localCacheBillingClient = CachedPurchaseDatabase.getInstance(application)
    }

    fun endDataSourceConnections() {
        playStoreBillingClient.endConnection()
        //normally you don't worry about closing a DB connection unless you have more than
        //one DB open. so no need to call 'localCacheBillingClient.close()'
        Log.d(javaClass.name, "endDataSourceConnections")
    }

    private fun instantiateAndConnectToPlayBillingService() {
        playStoreBillingClient = BillingClient.newBuilder(application.applicationContext)
                .setListener(this).build()
        connectToPlayBillingService()
    }

    /**
     * playStoreBillingClient.startConnection is an async call whose callback is
     * [onBillingSetupFinished]/ [onBillingServiceDisconnected]
     */
    private fun connectToPlayBillingService(): Boolean {
        Log.d(javaClass.name, "connectToPlayBillingService")
        if (!playStoreBillingClient.isReady) {
            playStoreBillingClient.startConnection(this)
            return true
        }
        return false
    }

    private fun buildSkuParams(@BillingClient.SkuType skuType: String, skuList: List<String>?): SkuDetailsParams {
        val params = SkuDetailsParams.newBuilder()
        params.setSkusList(skuList).setType(skuType)
        return params.build()
    }

    /**
     * This method is called by the [playStoreBillingClient] when new purchases are detected.
     * The purchase list from this method is not the same as the one from
     * [queryPurchases][BillingClient.queryPurchases]. Whereas queryPurchases returns everything
     * this user owns, [onPurchasesUpdated] only returns the items that were just now purchased or
     * billed.
     *
     * The purchases provided here should be passed along to the secure server for
     * [verification](https://developer.android.com/google/play/billing/billing_library_overview#Verify)
     * and safekeeping. And if this purchase is consumable, it should be consumed and the secure
     * server should be told of the consumption. All that is accomplished by calling
     * [queryPurchasesSync].
     */
    override fun onPurchasesUpdated(responseCode: Int, purchases: MutableList<Purchase>?) {
        purchaseUpdateListener(getResponseCode(responseCode), purchases?.map { AugmentedPurchase(it) }?.toMutableList())
    }

    override fun onBillingServiceDisconnected() {
        onBillingError(BillingClient.BillingResponse.SERVICE_DISCONNECTED, "Billing service disconnected")
    }

    /**
     * Presumably a set of SKUs has been defined on the Play Developer Console. This method is for
     * requesting a (improper) subset of those SKUs. Hence, the method accepts a list of product IDs
     * and returns the matching list of SkuDetails.
     *
     * The result is passed to [onSkuDetailsResponse]
     */
    private fun querySkuDetailsAsync(@BillingClient.SkuType skuType: String) {
        initiateBillingCallsAsync(playStoreBillingClient, this) {
            Log.d(javaClass.name, "querySkuDetailsAsync for $skuType")
            playStoreBillingClient.querySkuDetailsAsync(buildSkuParams(skuType, skuItemsMap[skuType]), this)
        }
    }

    /**
     * BACKGROUND
     *
     * Play Billing refers to receipts as [Purchases][Purchase]. So when a user buys something,
     * Play Billing returns a [Purchase] object that the app then uses to release the actual
     * [Entitlement] to the user. Receipts are pivotal within the [BillingRepositor]; but they are
     * not part of the repo’s public API because clients don’t need to know about them
     * (clients don’t need to know anything about billing).  At what moment the release of
     * entitlements actually takes place depends on the type of purchase. For consumable products,
     * the release may be deferred until after consumption by Play; for non-consumable products and
     * subscriptions, the release may be immediate. It is convenient to keep receipts from the local
     * cache for augmented security and for making some transactions easier.
     *
     * THIS METHOD
     *
     * [This method][queryPurchasesSync] grabs all the active purchases of this user and makes them
     * available to this app instance. Whereas this method plays a central role from the billing
     * system, it should be called liberally both within this repo and by clients. With that from
     * mind, the implementation must be assiduous from managing communication with data sources and from
     * processing the correlations amongst data types.
     *
     * Because purchase data is vital to the rest of the app, this method gets a call each time
     * the [BillingViewModel] successfully establishes connection with the Play [BillingClient]:
     * the call comes through [onBillingSetupFinished]. Recall also from Figure 2 that this method
     * gets called from inside [onPurchasesUpdated] from order not only to streamline the flow of
     * purchase data but also to seize on the opportunity to grab purchases from all possible
     * sources (i.e. Play and secure server).
     *
     * This method works by first grabbing both INAPP and SUBS purchases from the Play Store.
     * Then it checks if there is any new purchase (i.e. not yet recorded from the local cache).
     * If there are new purchases: it sends them to server for verification and safekeeping,
     * and it saves them from the local cache. But if there are no new purchases, it calls the
     * secure server to check for purchases there.
     *
     * Calls should not be made to the secure server too often -- it's co$tly. Instead calls should
     * be made at intervals, such as only allow calls at least 2 hours apart. On the one hand,
     * it is not good that users should not have access on Android to purchases they made through
     * another OS; on the other hand, it is not good to be paying too much to hosting companies
     * because the secure servers is being hit too often just to check for new purchases --
     * unless users are actually buying items very often across multiple devices.
     *
     * In this sample, the variables lastInvocationTime and DEAD_BAND are used to
     * [throttle][Throttle] calls to the secure server. lastInvocationTime could be persisted from
     * [Room]; here, [SharedPreferences] is used.
     */
    private fun queryPurchasesSync() {
        val purchasesResult = HashSet<Purchase>()
        when (billingOk) {
            BillingOK.QUERY_PURCHASES -> {
                var result = playStoreBillingClient.queryPurchases(BillingClient.SkuType.INAPP)
                result?.purchasesList?.apply { purchasesResult.addAll(this) }
                if (isSubscriptionSupported()) {
                    result = playStoreBillingClient.queryPurchases(BillingClient.SkuType.SUBS)
                    result?.purchasesList?.apply { purchasesResult.addAll(this) }
                }
            }
        }
        Log.d(javaClass.name, "All purchases " + purchasesResult.toString())
        purchases(purchasesResult.map { AugmentedPurchase(it) }.toList())
    }

    /**
     * This is the function to call when user wishes to make a purchase. This function will
     * launch the Play Billing flow. The response to this call is returned from
     * [onPurchasesUpdated]
     */
    override fun launchBillingFlow(augmentedSkuDetails: AugmentedSkuDetails): BillingPurchaseProvider {
        //val oldSku: String? = getOldSku(skuDetails.sku)
        val purchaseParams = BillingFlowParams.newBuilder().setSkuDetails(augmentedSkuDetails.skuDetails)./*setOldSku(oldSku).*/build()
        playStoreBillingClient.launchBillingFlow(activity, purchaseParams)
        return this
    }

    override fun launchBillingFlow(augmentedSkuDetails: AugmentedSkuDetails, result: (responseCode: BillingResponse, purchaseList: MutableList<AugmentedPurchase>?) -> Unit): BillingPurchaseProvider {
        purchaseUpdateListener = result
        val purchaseParams = BillingFlowParams.newBuilder().setSkuDetails(augmentedSkuDetails.skuDetails)./*setOldSku(oldSku).*/build()
        playStoreBillingClient.launchBillingFlow(activity, purchaseParams)
        return this
    }

    /**
     * Recall that Play Billing only supports two SKU types:
     * [in-app products][BillingClient.SkuType.INAPP] and
     * [subscriptions][BillingClient.SkuType.SUBS]. In-app products, as the name suggests, refer
     * to actual items that a user can buy, such as a house or food; while subscriptions refer to
     * services that a user must pay for regularly, such as auto-insurance. Naturally subscriptions
     * are not consumable -- it's not like you can eat your auto-insurance policy.
     *
     * Play Billing provides methods for consuming in-app products because they understand that
     * apps may sell items that users will keep forever (i.e. never consume) such as a house,
     * and items that users will need to keep buying such as food. Nevertheless, Play leaves the
     * distinction for which in-app products are consumable vs non-consumable entirely up to you.
     * In other words, inside your app you have the godlike power to decide whether it's logical for
     * people to eat their houses and live in their hotdogs or vice versa. BUT: once you tell
     * the Play Store to consume an in-app product, Play no longer tracks that purchase. Hence,
     * the app must implement logic here and on the secure server to track consumable items.
     *
     * So why would an app tell the Play Store to consume an item? That's because Play won't let
     * users buy items they already bought but haven't consumed. So if an app wants its users to
     * be able to keep buying an item, it must call [BillingClient.consumeAsync] each time they
     * buy it. In Trivial Drive for example consumeAsync is called each time the user buys gas;
     * otherwise they would never be able to buy gas or drive again once the tank becomes empty.
     */
    override fun consumePurchase(augmentedPurchase: AugmentedPurchase, listener: (responseCode: BillingResponse, purchaseToken: String?) -> Unit): BillingPurchaseProvider {
        purchaseConsumedListener = listener
        if (skuItemsMap.getOrElse(ProductCategory.IS_CONSUMABLE.name) { emptyList() }.contains(augmentedPurchase.purchase.sku)) {
            playStoreBillingClient.consumeAsync(augmentedPurchase.purchase.purchaseToken, this)
        } else {
            onBillingError(ERROR, "The product cannot be consumed")
            purchaseConsumedListener(BillingResponse.ERROR, null)
        }
        return this
    }

    /**
     * This is the callback for when connection to the Play [BillingClient] has been successfully
     * established. It might make sense to get [SkuDetails] and [Purchases][Purchase] at this point.
     */
    override fun onBillingSetupFinished(responseCode: Int) {
        Log.d(javaClass.name, "Billing setup Finished")
        when (responseCode) {
            BillingClient.BillingResponse.OK -> {
                Log.d(javaClass.name, "onBillingSetupFinished successfully")
                //resetConnectionRetryPolicyCounter()//for retry policy
                onBillingOk()
            }
            BillingClient.BillingResponse.BILLING_UNAVAILABLE -> {
                //Some apps may choose to make decisions based on this knowledge.
                onBillingError(BILLING_UNAVAILABLE, "onBillingSetupFinished but billing is not available on this device")
            }
            else -> {
                //do nothing. Someone else will connect it through retry policy.
                //May choose to send to server though
                onBillingError(responseCode, "onBillingSetupFinished with failure response code")
            }
        }
    }

    /**
     * Called by [playStoreBillingClient] to notify that a consume operation has finished.
     * Appropriate action should be taken in the app, such as add fuel to user's car.
     * This information should also be saved on the secure server in case user accesses the app
     * through another device.
     */
    override fun onConsumeResponse(responseCode: Int, purchaseToken: String?) {
        Log.d(javaClass.name, "on Purchase Consume Response")
        purchaseConsumedListener(getResponseCode(responseCode), purchaseToken)
    }

    /**
     * This is the callback from querySkuDetailsAsync. The local cache uses this response to create
     * an AugmentedSkuDetails list for the clients.
     */
    override fun onSkuDetailsResponse(responseCode: Int, skuDetailsList: MutableList<SkuDetails>?) {
        Log.d(javaClass.name, "Sku Details Response")
        Log.d(javaClass.name, queryCount.toString())
        when(billingOk){
            BillingOK.QUERY_BOTH->{
                queryCount++
                skuDetailsGroupList.addAll(skuDetailsList?.map { AugmentedSkuDetails(it) }?.toList()?: mutableListOf())
                if(queryCount==skuItemsMap.size){
                    skuDetailsResponseListener(getResponseCode(responseCode), skuDetailsGroupList.toMutableList())
                    resetskuDetailsGroupList()
                }
            }
            else->{
                skuDetailsResponseListener(getResponseCode(responseCode), skuDetailsList?.map { AugmentedSkuDetails(it) }?.toMutableList())
            }
        }
    }

    /**
     * All this is doing is check that billingClient is connected and if it's not, request
     * connection, wait x number of seconds and then proceed with the actual task.
     *
     * Runs from background thread, can be replaced with coroutine as well
     */
    private fun initiateBillingCallsAsync(billingClient: BillingClient, listener: BillingRepo, task: () -> Unit) {
        if (!billingClient.isReady) {
            Log.d(javaClass.name, "initiateBillingCallsAsync billing not ready")
            billingClient.startConnection(listener)
        } else {
            task()
        }

        //TODO add retry logic
        /*val process: () -> Unit = {
            delayUntilTrue(billingClient, {

            }, task)
        }
        process()*/
        /*RxUtils.runInBack(process, null) { *//*onComplete*//* }*/
    }

    private fun delayUntilTrue(billingClient: BillingClient, onNext: () -> Unit, taskOnComplete: () -> Unit) {
        RxUtils.withDelay(1000, billingClient.isReady, onNext, taskOnComplete)
    }

    /**
     * Checks if the user's device supports subscriptions
     */
    private fun isSubscriptionSupported(): Boolean {
        val responseCode = playStoreBillingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS)
        if (responseCode != BillingClient.BillingResponse.OK) {
            Log.w(javaClass.name, "isSubscriptionSupported() got an error response: $responseCode")
        }
        return responseCode == BillingClient.BillingResponse.OK
    }

    private fun resetskuDetailsGroupList(){
        skuDetailsGroupList.clear()
        queryCount=0
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: BillingRepo? = null

        fun getInstance(application: Application): BillingRepo =
                INSTANCE ?: synchronized(this) {
                    INSTANCE ?: BillingRepo(application).also { INSTANCE = it }
                }

        fun getResponseCode(responseCode: Int): BillingResponse = BillingResponse.values().first { responseCode == it.value }

        private fun mapToEnum(responseCode: Int): BillingResponse {
            return when (responseCode) {
                BillingResponse.OK.value -> BillingResponse.OK
                BillingResponse.USER_CANCELED.value -> BillingResponse.USER_CANCELED
                else -> BillingResponse.ERROR
            }
        }
    }
}