package com.slevinto.quietguest.billingrepo

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import com.android.billingclient.api.*
import com.slevinto.quietguest.billingrepo.BillingRepository.GameSku.GOLD_STATUS_SKUS
import com.slevinto.quietguest.billingrepo.BillingRepository.RetryPolicies.connectionRetryPolicy
import com.slevinto.quietguest.billingrepo.BillingRepository.RetryPolicies.resetConnectionRetryPolicyCounter
import com.slevinto.quietguest.billingrepo.BillingRepository.RetryPolicies.taskExecutionRetryPolicy
import com.slevinto.quietguest.billingrepo.BillingRepository.Throttle.isLastInvocationTimeStale
import com.slevinto.quietguest.billingrepo.BillingRepository.Throttle.refreshLastInvocationTime
import com.slevinto.quietguest.billingrepo.localdb.*
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow

class BillingRepository private constructor(private val application: Application) :
        PurchasesUpdatedListener, BillingClientStateListener,
        ConsumeResponseListener, SkuDetailsResponseListener {


    private lateinit var playStoreBillingClient: BillingClient
    private lateinit var secureServerBillingClient: BillingWebservice
    private lateinit var localCacheBillingClient: CachedPurchaseDatabase

    val subsSkuDetailsListLiveData: LiveData<List<AugmentedSkuDetails>> by lazy {
        if (!::localCacheBillingClient.isInitialized) {
            localCacheBillingClient = CachedPurchaseDatabase.getInstance(application)
        }
        localCacheBillingClient.skuDetailsDao().getSubscriptionSkuDetails()
    }

    /**
     * This list tells clients what in-app products are available for sale
     */
    val inappSkuDetailsListLiveData: LiveData<List<AugmentedSkuDetails>> by lazy {
        if (!::localCacheBillingClient.isInitialized) {
            localCacheBillingClient = CachedPurchaseDatabase.getInstance(application)
        }
        localCacheBillingClient.skuDetailsDao().getInappSkuDetails()
    }

    val gateOpensTankLiveData: LiveData<GateOpensTank> by lazy {
        if (!::localCacheBillingClient.isInitialized) {
            localCacheBillingClient = CachedPurchaseDatabase.getInstance(application)
        }
        localCacheBillingClient.entitlementsDao().getGateOpensTank()
    }

    val goldStatusLiveData: LiveData<GoldStatus> by lazy {
        if (!::localCacheBillingClient.isInitialized) {
            localCacheBillingClient = CachedPurchaseDatabase.getInstance(application)
        }
        localCacheBillingClient.entitlementsDao().getGoldStatus()
    }

    fun startDataSourceConnections() {
        Log.d(LOG_TAG, "startDataSourceConnections")
        instantiateAndConnectToPlayBillingService()
        secureServerBillingClient = BillingWebservice.create()
        localCacheBillingClient = CachedPurchaseDatabase.getInstance(application)
    }

    fun endDataSourceConnections() {
        playStoreBillingClient.endConnection()
        //normally you don't worry about closing a DB connection unless you have more than
        //one DB open. so no need to call 'localCacheBillingClient.close()'
        Log.d(LOG_TAG, "startDataSourceConnections")
    }

    private fun instantiateAndConnectToPlayBillingService() {
        playStoreBillingClient = BillingClient.newBuilder(application.applicationContext)
                .setListener(this).build()
        connectToPlayBillingService()
    }

    private fun connectToPlayBillingService(): Boolean {
        Log.d(LOG_TAG, "connectToPlayBillingService")
        if (!playStoreBillingClient.isReady) {
            playStoreBillingClient.startConnection(this)
            return true
        }
        return false
    }

    override fun onBillingSetupFinished(responseCode: Int) {
        when (responseCode) {
            BillingClient.BillingResponse.OK -> {
                Log.d(LOG_TAG, "onBillingSetupFinished successfully")
                resetConnectionRetryPolicyCounter()//for retry policy
                querySkuDetailsAsync(BillingClient.SkuType.INAPP, GameSku.INAPP_SKUS)
                querySkuDetailsAsync(BillingClient.SkuType.SUBS, GameSku.SUBS_SKUS)
                queryPurchasesAsync()
            }
            BillingClient.BillingResponse.BILLING_UNAVAILABLE -> {
                //Some apps may choose to make decisions based on this knowledge.
                Log.d(LOG_TAG, "onBillingSetupFinished but billing is not available on this device")
            }
            else -> {
                //do nothing. Someone else will connect it through retry policy.
                //May choose to send to server though
                Log.d(LOG_TAG, "onBillingSetupFinished with failure response code: $responseCode")
            }
        }
    }

    override fun onBillingServiceDisconnected() {
        Log.d(LOG_TAG, "onBillingServiceDisconnected")
        connectionRetryPolicy { connectToPlayBillingService() }
    }

    fun queryPurchasesAsync() {
        fun task() {
            Log.d(LOG_TAG, "queryPurchasesAsync called")
            val purchasesResult = HashSet<Purchase>()
            var result = playStoreBillingClient.queryPurchases(BillingClient.SkuType.INAPP)
            Log.d(LOG_TAG, "queryPurchasesAsync INAPP results: ${result?.purchasesList}")
            result?.purchasesList?.apply { purchasesResult.addAll(this) }
            if (isSubscriptionSupported()) {
                result = playStoreBillingClient.queryPurchases(BillingClient.SkuType.SUBS)
                result?.purchasesList?.apply { purchasesResult.addAll(this) }
                Log.d(LOG_TAG, "queryPurchasesAsync SUBS results: ${result?.purchasesList}")
            }

            processPurchases(purchasesResult)
        }
        taskExecutionRetryPolicy(playStoreBillingClient, this) { task() }
    }

    private fun processPurchases(purchasesResult: Set<Purchase>) = CoroutineScope(Job() + Dispatchers.IO).launch {
        val cachedPurchases = localCacheBillingClient.purchaseDao().getPurchases()
        val newBatch = HashSet<Purchase>(purchasesResult.size)
        purchasesResult.forEach { purchase ->
            if (isSignatureValid(purchase) && !cachedPurchases.any { it.data == purchase }) {//todo !cachedPurchases.contains(purchase)
                newBatch.add(purchase)
            }
        }

        if (newBatch.isNotEmpty()) {
            sendPurchasesToServer(newBatch)
            // We still care about purchasesResult in case a old purchase has not yet been consumed.
            saveToLocalDatabase(newBatch, purchasesResult)
            //consumeAsync(purchasesResult): do this inside saveToLocalDatabase to avoid race condition
        } else if (isLastInvocationTimeStale(application)) {
            handleConsumablePurchasesAsync(purchasesResult)
            queryPurchasesFromSecureServer()
        }
    }

    private fun isSignatureValid(purchase: Purchase): Boolean {
        return Security.verifyPurchase(Security.BASE_64_ENCODED_PUBLIC_KEY, purchase.originalJson, purchase.signature)
    }

    private fun queryPurchasesFromSecureServer() {
        /* TODO FIXME:  This is not a real implementation. If you actually have a server, you must

            This is not complicated. All you are doing is call the server and the server should
            return all the active purchases it has for this user. Here are the steps

            1- use retrofit with coroutine or RxJava to get all active purchases from the server
            2 - compare the purchases in the local cache with those return by server
            3 - if local cache has purchases that's not in the list from server, send those
                purchases to server for investigation: you may be dealing with fraud.
            4 - Otherwise, update the local cache with the data from server with something like

            ```
            localCacheBillingClient.purchaseDao().deleteAll()
            saveToLocalDatabase(secureServerResult)
            ```
            It's important to use saveToLocalDatabase so as to update the Entitlements in passing.

            5 - refresh lastInvocationTime.
         */
        fun getPurchasesFromSecureServerToLocalDB() {
            //steps 1 to 4 go in here
        }
        getPurchasesFromSecureServerToLocalDB()

        //this is step 5: refresh lastInvocationTime.
        //This is an important part of the throttling mechanism. Don't forget to do it
        refreshLastInvocationTime(application)

        //TODO: FIXME: Again, this is not a real implementation. You must implement this yourself
    }


    private fun sendPurchasesToServer(purchases: Set<Purchase>) {
        /*
        TODO if you have a server:
        send purchases to server using maybe

         `secureServerBillingClient.updateServer(newBatch.toSet())`

         and then after server has processed the information, then get purchases from server using
         [queryPurchasesFromSecureServer], which will help clean up the local cache
         */
    }

    private fun handleConsumablePurchasesAsync(purchases: Set<Purchase>) {
        purchases.forEach {
            if (GameSku.CONSUMABLE_SKUS.contains(it.sku)) {
                playStoreBillingClient.consumeAsync(it.purchaseToken, this@BillingRepository)
                //tell your server:
                Log.i(LOG_TAG, "handleConsumablePurchasesAsync: asked Play Billing to consume sku = ${it.sku}")
            }
        }
    }

    private fun isSubscriptionSupported(): Boolean {
        val responseCode = playStoreBillingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS)
        if (responseCode != BillingClient.BillingResponse.OK) {
            Log.w(LOG_TAG, "isSubscriptionSupported() got an error response: $responseCode")
        }
        return responseCode == BillingClient.BillingResponse.OK
    }

    private fun querySkuDetailsAsync(@BillingClient.SkuType skuType: String, skuList: List<String>) {
        val params = SkuDetailsParams.newBuilder()
        params.setSkusList(skuList).setType(skuType)
        taskExecutionRetryPolicy(playStoreBillingClient, this) {
            Log.d(LOG_TAG, "querySkuDetailsAsync for $skuType")
            playStoreBillingClient.querySkuDetailsAsync(params.build(), this)

        }
    }

    override fun onSkuDetailsResponse(responseCode: Int, skuDetailsList: MutableList<SkuDetails>?) {
        if (responseCode != BillingClient.BillingResponse.OK) {
            Log.w(LOG_TAG, "SkuDetails query failed with response: $responseCode")
        } else {
            Log.d(LOG_TAG, "SkuDetails query responded with success. List: $skuDetailsList")
        }

        if (skuDetailsList.orEmpty().isNotEmpty()) {
            val scope = CoroutineScope(Job() + Dispatchers.IO)
            scope.launch {
                skuDetailsList?.forEach { localCacheBillingClient.skuDetailsDao().insertOrUpdate(it) }
            }
        }
    }

    fun launchBillingFlow(activity: Activity, augmentedSkuDetails: AugmentedSkuDetails) =
            launchBillingFlow(activity, SkuDetails(augmentedSkuDetails.originalJson))

    private fun launchBillingFlow(activity: Activity, skuDetails: SkuDetails) {
        val oldSku: String? = getOldSku(skuDetails.sku)
        val purchaseParams = BillingFlowParams.newBuilder().setSkuDetails(skuDetails)
                .setOldSku(oldSku).build()

        taskExecutionRetryPolicy(playStoreBillingClient, this) {
            playStoreBillingClient.launchBillingFlow(activity, purchaseParams)
        }
    }

    private fun getOldSku(sku: String?): String? {
        var result: String? = null
        if (GameSku.SUBS_SKUS.contains(sku)) {
            goldStatusLiveData.value?.apply {
                result = when (sku) {
                    GameSku.GOLD_MONTHLY -> GameSku.GOLD_MONTHLY
                    else -> GameSku.GOLD_MONTHLY
                }
            }
        }
        return result
    }

    override fun onPurchasesUpdated(responseCode: Int, purchases: MutableList<Purchase>?) {
        when (responseCode) {
            BillingClient.BillingResponse.OK -> {
                // will handle server verification, consumables, and updating the local cache
                purchases?.apply { processPurchases(this.toSet()) }
            }
            BillingClient.BillingResponse.ITEM_ALREADY_OWNED -> {
                //item already owned? call queryPurchasesAsync to verify and process all such items
                Log.d(LOG_TAG, "already owned items")
                queryPurchasesAsync()
            }
            BillingClient.BillingResponse.DEVELOPER_ERROR -> {
                Log.e(
                    LOG_TAG, "Your app's configuration is incorrect. Review in the Google Play" +
                        "Console. Possible causes of this error include: APK is not signed with " +
                        "release key; SKU productId mismatch.")
            }
            else -> {
                Log.i(LOG_TAG, "BillingClient.BillingResponse error code: $responseCode")
            }
        }
    }

   override fun onConsumeResponse(responseCode: Int, purchaseToken: String?) {
        Log.d(LOG_TAG, "onConsumeResponse")
        when (responseCode) {
            BillingClient.BillingResponse.OK -> {
                //give user the items s/he just bought by updating the appropriate tables/databases
                purchaseToken?.apply { saveToLocalDatabase(this) }
                secureServerBillingClient.onComsumeResponse(purchaseToken, responseCode)
            }
            else -> {
                Log.w(
                    LOG_TAG, "Error consuming purchase with token ($purchaseToken). " +
                        "Response code: $responseCode")
            }
        }
    }

    private fun saveToLocalDatabase(newBatch: Set<Purchase>, allPurchases: Set<Purchase>) {
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        scope.launch {
            newBatch.forEach { purchase ->
                when (purchase.sku) {
                    GameSku.GOLD_MONTHLY -> {
                        val goldStatus = GoldStatus(true)
                        insert(goldStatus)
                        localCacheBillingClient.skuDetailsDao().insertOrUpdate(purchase.sku, goldStatus.mayPurchase())
                        /*there are more than one way to buy gold status. After disabling the one the user
                        * just purchased, re-enble the others*/
                        GOLD_STATUS_SKUS.forEach { otherSku ->
                            if (otherSku != purchase.sku) {
                                localCacheBillingClient.skuDetailsDao().insertOrUpdate(otherSku, !goldStatus.mayPurchase())
                            }
                        }
                    }
                }

            }
            localCacheBillingClient.purchaseDao().insert(*newBatch.toTypedArray())
            /*
            Consumption should happen here so as not to be concerned about race conditions.
            If [consumeAsync] were to be called inside [queryPurchasesAsync], [onConsumeResponse]
            might possibly return before [saveToLocalDatabase] had had a chance to actually persist
            the item in question.

            allPurchases instead of newBatch is used in case a previous purchase was not yet
            consumed. In such case newBatch will be empty but not allPurchases.
             */
            handleConsumablePurchasesAsync(allPurchases)
        }
    }

    private fun saveToLocalDatabase(purchaseToken: String) {
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        scope.launch {
            val cachedPurchases = localCacheBillingClient.purchaseDao().getPurchases()
            val match = cachedPurchases.find { it.purchaseToken == purchaseToken }
            if (match?.sku == GameSku.gateOpenCount) {
                updateGateOpensTank(GateOpensTank(gateOpensPurchase))
                /**
                 * This saveToLocalDatabase method was called because Play called onConsumeResponse.
                 * So if you think of a Purchase as a receipt, you no longer need to keep a copy of
                 * the receipt in the local cache since the user has just consumed the product.
                 */
                localCacheBillingClient.purchaseDao().delete(match)
            }
        }
    }

    @WorkerThread
    suspend fun updateGateOpensTank(gateOpens: GateOpensTank) = withContext(Dispatchers.IO) {
        Log.d(LOG_TAG, "updateGateOpensTank")
        var update: GateOpensTank = gateOpens
        gateOpensTankLiveData.value?.apply {
            synchronized(this) {
                if (this != gateOpens) {//new purchase
                    update = GateOpensTank(getLevel() + gateOpens.getLevel())
                }
                Log.d(LOG_TAG, "New purchase level is ${gateOpens.getLevel()}; existing level is ${getLevel()}; so the final result is ${update.getLevel()}")
                localCacheBillingClient.entitlementsDao().update(update)
            }
        }
        if (gateOpensTankLiveData.value == null) {
            localCacheBillingClient.entitlementsDao().insert(update)
            Log.d(LOG_TAG, "No we just added from null gateOpens with level: ${gateOpens.getLevel()}")
        }
        localCacheBillingClient.skuDetailsDao().insertOrUpdate(GameSku.gateOpenCount, update.mayPurchase())
        Log.d(LOG_TAG, "updated AugmentedSkuDetails as well")
    }

    @WorkerThread
    private suspend fun insert(entitlement: Entitlement) = withContext(Dispatchers.IO) {
        localCacheBillingClient.entitlementsDao().insert(entitlement)
    }

    companion object {
        private const val LOG_TAG = "BillingRepository"

        @Volatile
        private var INSTANCE: BillingRepository? = null

        fun getInstance(application: Application): BillingRepository =
                INSTANCE ?: synchronized(this) {
                    INSTANCE
                            ?: BillingRepository(application)
                                    .also { INSTANCE = it }
                }

    }

    private object Throttle {
        private const val DEAD_BAND = 7200000//2*60*60*1000: two hours wait
        private const val PREFS_NAME = "BillingRepository.Throttle"
        private const val KEY = "lastInvocationTime"

        fun isLastInvocationTimeStale(context: Context): Boolean {
            val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastInvocationTime = sharedPrefs.getLong(KEY, 0)
            return lastInvocationTime + DEAD_BAND < Date().time
        }

        fun refreshLastInvocationTime(context: Context) {
            val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            with(sharedPrefs.edit()) {
                putLong(KEY, Date().time)
                apply()
            }
        }
    }

   private object RetryPolicies {
        private const val maxRetry = 5
        private var retryCounter = AtomicInteger(1)
        private const val baseDelayMillis = 500
        private const val taskDelay = 2000L

        fun resetConnectionRetryPolicyCounter() {
            retryCounter.set(1)
        }

        fun connectionRetryPolicy(block: () -> Unit) {
            Log.d(LOG_TAG, "connectionRetryPolicy")
            val scope = CoroutineScope(Job() + Dispatchers.Main)
            scope.launch {
                val counter = retryCounter.getAndIncrement()
                if (counter < maxRetry) {
                    val waitTime: Long = (2f.pow(counter) * baseDelayMillis).toLong()
                    delay(waitTime)
                    block()
                }
            }

        }

        fun taskExecutionRetryPolicy(billingClient: BillingClient, listener: BillingRepository, task: () -> Unit) {
            val scope = CoroutineScope(Job() + Dispatchers.Main)
            scope.launch {
                if (!billingClient.isReady) {
                    Log.d(LOG_TAG, "taskExecutionRetryPolicy billing not ready")
                    billingClient.startConnection(listener)
                    delay(taskDelay)
                }
                task()
            }
        }
    }

    private object GameSku {
        const val gateOpenCount = "gate_open_count"
        const val GOLD_MONTHLY = "slevinto.quietguest.basic_subscription"

        val INAPP_SKUS = listOf(gateOpenCount)
        val SUBS_SKUS = listOf(GOLD_MONTHLY)
        val CONSUMABLE_SKUS = listOf(gateOpenCount)
        val GOLD_STATUS_SKUS = SUBS_SKUS//coincidence that there only gold_status is a sub
    }
}

