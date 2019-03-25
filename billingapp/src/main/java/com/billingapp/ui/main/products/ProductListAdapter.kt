package com.billingapp.ui.main.products

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.billingapp.R
import kotlinx.android.synthetic.main.item_product.view.*
import my.android.inappbilling.AugmentedSkuDetails

class ProductListAdapter(val list: MutableList<AugmentedSkuDetails>, val listener: (pos: Int, data: AugmentedSkuDetails) -> Unit) : RecyclerView.Adapter<ProductListAdapter.ProductListViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductListViewHolder {
        return ProductListViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_product, parent, false), listener)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun onBindViewHolder(holder: ProductListViewHolder, position: Int) {
        val view = holder.itemView
        view.textProductName.text = list[position].skuDetails.title
        view.textProductPrice.text = list[position].skuDetails.price
        view.btnPurchase.setOnClickListener { listener(position, list[position]) }
    }


    class ProductListViewHolder(view: View, listener: (pos: Int, data: AugmentedSkuDetails) -> Unit) : RecyclerView.ViewHolder(view)
}