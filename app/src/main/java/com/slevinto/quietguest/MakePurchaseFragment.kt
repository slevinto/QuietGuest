package com.slevinto.quietguest

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
//import com.kotlin.trivialdrive.adapters.SkuDetailsAdapter
import com.slevinto.quietguest.billingrepo.localdb.AugmentedSkuDetails
import com.slevinto.quietguest.viewmodels.BillingViewModel
import com.slevinto.quietguest.adapters.SkuDetailsAdapter
import kotlinx.android.synthetic.main.fragment_make_purchase.view.*

class MakePurchaseFragment : Fragment() {

    val LOG_TAG = "MakePurchaseFragment"
    private lateinit var billingViewModel: BillingViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_make_purchase, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(LOG_TAG, "onViewCreated")

        val inappAdapter = object : SkuDetailsAdapter() {
            override fun onSkuDetailsClicked(item: AugmentedSkuDetails) {
                onPurchase(view, item)
            }
        }

        val subsAdapter = object : SkuDetailsAdapter() {
            override fun onSkuDetailsClicked(item: AugmentedSkuDetails) {
                onPurchase(view, item)
            }
        }
        attachAdapterToRecyclerView(view.inapp_inventory, inappAdapter)
        attachAdapterToRecyclerView(view.subs_inventory, subsAdapter)

        billingViewModel = ViewModelProviders.of(this).get(BillingViewModel::class.java)
        billingViewModel.inappSkuDetailsListLiveData.observe(this, Observer {
            it?.let { inappAdapter.setSkuDetailsList(it) }
        })
        billingViewModel.subsSkuDetailsListLiveData.observe(this, Observer {
            it?.let { subsAdapter.setSkuDetailsList(it) }
        })

    }

    private fun attachAdapterToRecyclerView(recyclerView: RecyclerView, skuAdapter: SkuDetailsAdapter) {
        with(recyclerView) {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = skuAdapter
        }
    }

    private fun onPurchase(view: View, item: AugmentedSkuDetails) {
        billingViewModel.makePurchase(activity as Activity, item)
        view.findNavController().navigate(R.id.action_playGame)
        Log.d(LOG_TAG, "starting purchase flow for SkuDetail:\n ${item}")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val f = fragmentManager!!
            .findFragmentById(R.id.action_makePurchase) 
        if (f != null)
            fragmentManager!!.beginTransaction().remove(f).commit()
    }
}
