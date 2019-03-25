package com.billingapp.ui.main.purchases

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.billingapp.R
import com.billingapp.model.CONSUMABLE_SKUS
import com.billingapp.ui.main.AugmentedPurchase.ProductPurchasedAdapter
import com.billingapp.ui.main.MainViewModel
import kotlinx.android.synthetic.main.purchase_list_fragment.*
import my.android.inappbilling.AugmentedPurchase
import my.android.inappbilling.BillingRepo
import my.android.inappbilling.BillingResponse
import my.android.inappbilling.enums.BillingOK
import my.android.inappbilling.enums.ProductCategory

class ProductPurchasedFragment : Fragment() {

    companion object {
        fun newInstance() = ProductPurchasedFragment()
    }

    private lateinit var viewModel: MainViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.purchase_list_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)
        // TODO: Use the ViewModel

        BillingRepo.getInstance(activity?.application!!)
                .startDataSourceConnections()
                .onBillingOk(BillingOK.QUERY_PURCHASES)
                .onPurchasesQueryResult {
                    rvPurchaseList?.adapter = ProductPurchasedAdapter(it) { pos, data ->
                        // on purchase item click, consume the inapp purchase
                        consumePurchase(data)
                    }
                }
        btnBack.setOnClickListener { activity?.supportFragmentManager?.popBackStack() }
    }

    private fun consumePurchase(purchase: AugmentedPurchase){
        BillingRepo.getInstance(activity?.application!!)
            .items(ProductCategory.IS_CONSUMABLE.name, CONSUMABLE_SKUS)
            .consumePurchase(purchase){ responseCode, purchaseToken ->
                when(responseCode){
                    BillingResponse.OK ->{
                        Log.d(javaClass.name, "Product was consumed successfully")
                        Toast.makeText(requireContext(),"Product was consumed successfully,\n Now you can lock your paid content \n And notify server", LENGTH_SHORT).show()
                    }
                    else -> {
                        Log.d(javaClass.name, "Product was NOT consumed/ Already Consumed $responseCode")
                        Toast.makeText(requireContext(),"Product couldn't be consumed. May be this is a one time product / Subscription or already consumed", LENGTH_SHORT).show()
                    }
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        BillingRepo.getInstance(activity?.application!!).endDataSourceConnections()
    }

}
