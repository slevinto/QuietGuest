package com.slevinto.quietguest.viewmodels

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.slevinto.quietguest.billingrepo.BillingRepository
import com.slevinto.quietguest.billingrepo.localdb.AugmentedSkuDetails
import com.slevinto.quietguest.billingrepo.localdb.GateOpensTank
import com.slevinto.quietguest.billingrepo.localdb.GoldStatus
import kotlinx.coroutines.*

class BillingViewModel(application: Application) : AndroidViewModel(application) {

    var gateOpensTankLiveData: LiveData<GateOpensTank>
    val goldStatusLiveData: LiveData<GoldStatus>
    val subsSkuDetailsListLiveData: LiveData<List<AugmentedSkuDetails>>
    val inappSkuDetailsListLiveData: LiveData<List<AugmentedSkuDetails>>

    private val logTag = "BillingViewModel"
    private val viewModelScope = CoroutineScope(Job() + Dispatchers.Main)
    private var repository: BillingRepository = BillingRepository.getInstance(application)

    init {
        repository.startDataSourceConnections()
        gateOpensTankLiveData = repository.gateOpensTankLiveData
        goldStatusLiveData = repository.goldStatusLiveData
        subsSkuDetailsListLiveData = repository.subsSkuDetailsListLiveData
        inappSkuDetailsListLiveData = repository.inappSkuDetailsListLiveData
    }

    /**
     * Not used in this sample app. But you may want to force refresh in your own app (e.g.
     * pull-to-refresh)
     */
    fun queryPurchases() = repository.queryPurchasesAsync()

    override fun onCleared() {
        super.onCleared()
        Log.d(logTag, "onCleared")
        repository.endDataSourceConnections()
        viewModelScope.coroutineContext.cancel()
    }

    fun makePurchase(activity: Activity, augmentedSkuDetails: AugmentedSkuDetails) {
        repository.launchBillingFlow(activity, augmentedSkuDetails)
    }

    /**
     * It's important to save after decrementing since gas can be updated by both clients and
     * the data sources.
     *
     * Note that even the ViewModel does not need to worry about thread safety because the
     * repo has already taken care it. So definitely the clients also don't need to worry about
     * thread safety.
     */
    fun decrementAndSaveGateOpens() {
        val gateOpens: GateOpensTank?
        when (gateOpensTankLiveData.value) {
            null ->  gateOpens = GateOpensTank(10)
            else -> gateOpens = gateOpensTankLiveData.value
        }
        gateOpens?.apply {
            decrement()
            viewModelScope.launch {
                repository.updateGateOpensTank(this@apply)
            }
        }
    }


}