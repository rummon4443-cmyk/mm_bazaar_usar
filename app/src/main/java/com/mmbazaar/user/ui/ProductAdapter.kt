package com.mmbazaar.user.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.mmbazaar.user.R
import com.mmbazaar.user.databinding.ItemProductBinding
import org.json.JSONObject

class ProductAdapter(
    private val products: MutableList<JSONObject>,
    private val isWishlisted: (String) -> Boolean,
    private val onOpen: (JSONObject) -> Unit,
    private val onAdd: (JSONObject) -> Unit,
    private val onWish: (JSONObject) -> Unit,
    private val showBrand: () -> Boolean = { true },
    private val showOldPrice: () -> Boolean = { true }
) : RecyclerView.Adapter<ProductAdapter.Holder>() {
    inner class Holder(val b: ItemProductBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = products.size
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val p = products[position]; val id = p.optString("id")
        holder.b.tvTitle.text = p.optString("title", "পণ্য")
        holder.b.tvBrand.text = if (showBrand()) p.optString("brand") else ""
        holder.b.tvBrand.visibility = if (showBrand() && p.optString("brand").isNotBlank()) android.view.View.VISIBLE else android.view.View.GONE
        val old = p.optDouble("old_price", 0.0)
        holder.b.tvPrice.text = if (showOldPrice() && old > p.optDouble("price", 0.0)) "৳ ${p.optDouble("price").toLong()}  ·  আগে ৳ ${old.toLong()}" else "৳ ${p.optDouble("price", 0.0).toLong()}"
        val variantStock = p.optJSONObject("variant_stock")
        val hasVariantAvailable = variantStock != null && variantStock.keys().asSequence().any { variantStock.optInt(it, 0) > 0 }
        val stockAvailable = p.optInt("stock") > 0 || hasVariantAvailable
        holder.b.tvStock.text = if (stockAvailable) "স্টক ${p.optInt("stock")}" else "স্টক শেষ"
        holder.b.ivProduct.load(p.optString("image_url").ifBlank { null }) { crossfade(true); placeholder(R.drawable.bg_card); error(R.drawable.bg_card) }
        holder.b.tvWish.text = if (isWishlisted(id)) "♥" else "♡"
        holder.b.tvWish.setOnClickListener { onWish(p); notifyItemChanged(position) }
        holder.b.btnAdd.isEnabled = stockAvailable
        holder.b.btnAdd.text = if (stockAvailable) "বিস্তারিত / কার্টে যোগ" else "স্টক শেষ"
        holder.b.btnAdd.setOnClickListener { onAdd(p) }
        holder.b.root.setOnClickListener { onOpen(p) }
    }
    fun replace(list: List<JSONObject>) { products.clear(); products.addAll(list); notifyDataSetChanged() }
}
