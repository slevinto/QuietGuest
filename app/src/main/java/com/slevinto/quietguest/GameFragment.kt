package com.slevinto.quietguest

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.findNavController
import com.slevinto.quietguest.billingrepo.localdb.GateOpensTank
import com.slevinto.quietguest.viewmodels.BillingViewModel
import kotlinx.android.synthetic.main.fragment_game.*

class GameFragment : androidx.fragment.app.Fragment() {
    private var gateOpensLevel: GateOpensTank? = null
    private lateinit var billingViewModel: BillingViewModel
    private var gateOpensEntitle: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, containter: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_game, containter, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btn_drive.setOnClickListener { onGateOpen() }
        btn_purchase.setOnClickListener { onPurchase(it) }
        btn_drive.visibility = View.GONE

        billingViewModel = ViewModelProviders.of(this).get(BillingViewModel::class.java)
        billingViewModel.gateOpensTankLiveData.observe(this, Observer {
            gateOpensLevel = it

            showGateOpensLevel()
        })

        billingViewModel.goldStatusLiveData.observe(this, Observer {
            it?.apply { gateOpensEntitle = showGoldStatus(entitled) }
        })

        // val listP = billingViewModel.queryPurchases()
    }

    fun onGateOpen(): Boolean {
        gateOpensLevel?.apply {
            if (!needGateOpens() || gateOpensEntitle) {
                if (!needGateOpens()) {
                    billingViewModel.decrementAndSaveGateOpens()
                    showGateOpensLevel()
                }
                Toast.makeText(context, getString(R.string.alert_gate_opens), Toast.LENGTH_LONG).show()
            }
        }
        if ((gateOpensLevel?.needGateOpens() != false) && !gateOpensEntitle) {
            Toast.makeText(context, getString(R.string.alert_no_gate_opens), Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    private fun onPurchase(view: View) {
        view.findNavController().navigate(R.id.action_makePurchase)
    }

    private fun showGateOpensLevel() {
        gateOpensLevel?.apply {
            gate_opens_count.text = getLevel().toString()
        }
        if (gateOpensLevel == null) {
            gateOpensLevel = GateOpensTank(10)
            gate_opens_count.text = 10.toString()
        }
    }

    private fun showGoldStatus(entitled: Boolean): Boolean {
        return entitled
    }
}
