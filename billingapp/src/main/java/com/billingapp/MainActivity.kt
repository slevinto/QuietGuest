package com.billingapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.billingapp.logic.contracts.IMainActivityContract
import com.billingapp.logic.contracts.IMainPresenterContract
import com.billingapp.ui.main.MainPresenter
import com.billingapp.ui.main.products.ProductListFragment
import com.billingapp.ui.main.purchases.ProductPurchasedFragment

class MainActivity : AppCompatActivity(), IMainActivityContract {
    override var presenter: IMainPresenterContract? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.container, ProductListFragment.newInstance())
                    .commitNow()
        }
    }

    override fun onStart() {
        super.onStart()
        presenter = MainPresenter(this)
    }

    fun navigateToViewPurchaseFragment() {
        supportFragmentManager.beginTransaction()
                .replace(R.id.container, ProductPurchasedFragment.newInstance())
                .addToBackStack(null)
                .commit()
    }

}
