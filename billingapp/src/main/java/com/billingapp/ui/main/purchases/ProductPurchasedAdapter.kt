package com.billingapp.ui.main.AugmentedPurchase

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.billingapp.R
import kotlinx.android.synthetic.main.item_purchase.view.*
import my.android.inappbilling.AugmentedPurchase
import java.util.*

class ProductPurchasedAdapter(val list: List<AugmentedPurchase>, val listener: (pos: Int, data: AugmentedPurchase) -> Unit) : RecyclerView.Adapter<ProductPurchasedAdapter.ProductListViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductListViewHolder {
        return ProductListViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_purchase, parent, false), listener)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun onBindViewHolder(holder: ProductListViewHolder, position: Int) {
        val view = holder.itemView
        view.textProductName.text = list[position].purchase.originalJson
        view.textProductPrice.text = Date(list[position].purchase.purchaseTime).toString()
        view.btnConsume.setOnClickListener { listener(position, list[position]) }
    }


    class ProductListViewHolder(view: View, listener: (pos: Int, data: AugmentedPurchase) -> Unit) : RecyclerView.ViewHolder(view)
}