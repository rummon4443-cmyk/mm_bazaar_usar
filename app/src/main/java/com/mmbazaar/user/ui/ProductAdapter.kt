package com.mmbazaar.user.ui

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
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
    private val showOldPrice: () -> Boolean = { true },
    private val onBuyNow: ((JSONObject) -> Unit)? = null,
    private val showWishlist: () -> Boolean = { true }
) : RecyclerView.Adapter<ProductAdapter.Holder>() {
    inner class Holder(val b: ItemProductBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = products.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val p = products[position]
        val id = p.optString("id")
        val price = p.optDouble("price", 0.0)
        val old = p.optDouble("old_price", 0.0).takeIf { it <= 0.0 } ?: p.optDouble("oldPrice", 0.0)
        val stock = p.optInt("stock", 0)
        val variantStock = p.optJSONObject("variant_stock")
        val variantAvailable = variantStock != null && variantStock.keys().asSequence().any { variantStock.optInt(it, 0) > 0 }
        val stockAvailable = stock > 0 || variantAvailable
        val rating = p.optDouble("avg_rating", p.optDouble("avgRating", 0.0))
        val reviewCount = p.optInt("review_count", p.optInt("reviewCount", 0))
        val flash = p.optBoolean("is_flash_sale", p.optBoolean("isFlashSale", false))

        holder.b.tvTitle.text = p.optString("title", "পণ্য")
        holder.b.tvBrand.text = if (showBrand()) p.optString("brand") else ""
        holder.b.tvBrand.visibility = if (showBrand() && p.optString("brand").isNotBlank()) View.VISIBLE else View.GONE
        holder.b.tvPrice.text = "৳ ${money(price)}"
        holder.b.tvOldPrice.visibility = if (showOldPrice() && old > price) View.VISIBLE else View.GONE
        holder.b.tvOldPrice.text = if (old > price) "৳ ${money(old)}" else ""
        holder.b.tvOldPrice.paintFlags = holder.b.tvOldPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        holder.b.tvDiscount.visibility = if (showOldPrice() && old > price) View.VISIBLE else View.GONE
        holder.b.tvDiscount.text = if (showOldPrice() && old > price) "-${(((old - price) / old) * 100).toInt()}%" else ""
        holder.b.tvRating.visibility = if (rating > 0 && reviewCount > 0) View.VISIBLE else View.GONE
        holder.b.tvRating.text = if (rating > 0 && reviewCount > 0) "★ ${String.format(java.util.Locale.US, "%.1f", rating)} ($reviewCount)" else ""
        holder.b.tvFlash.visibility = if (flash) View.VISIBLE else View.GONE
        holder.b.tvLowStock.visibility = if (stockAvailable && stock in 1..4) View.VISIBLE else View.GONE
        holder.b.tvLowStock.text = if (stock in 1..4) "মাত্র $stock টি বাকি" else ""
        holder.b.tvStock.text = if (stockAvailable) "স্টকে আছে" else "স্টক শেষ"
        holder.b.tvStock.setTextColor(holder.itemView.context.getColor(if (stockAvailable) R.color.success else R.color.danger))
        holder.b.ivProduct.load(p.optString("image_url").ifBlank { p.optString("imageUrl").ifBlank { null } }) {
            crossfade(true)
            placeholder(R.drawable.bg_card)
            error(R.drawable.bg_card)
        }
        holder.b.tvWish.text = if (isWishlisted(id)) "♥" else "♡"
        holder.b.tvWish.visibility = if (showWishlist()) View.VISIBLE else View.GONE
        holder.b.tvWish.setOnClickListener { onWish(p); notifyItemChanged(holder.bindingAdapterPosition) }
        holder.b.btnAdd.isEnabled = stockAvailable
        holder.b.btnBuyNow.isEnabled = stockAvailable
        holder.b.btnAdd.text = if (stockAvailable) "🛒 যোগ করুন" else "স্টক নেই"
        holder.b.btnAdd.setOnClickListener { onAdd(p) }
        holder.b.btnBuyNow.text = "⚡ এখনই কিনুন"
        holder.b.btnBuyNow.setOnClickListener { (onBuyNow ?: onAdd).invoke(p) }
        holder.b.root.setOnClickListener { onOpen(p) }
    }

    private fun money(value: Double) = String.format(java.util.Locale.US, "%,.0f", value)

    fun replace(list: List<JSONObject>) {
        products.clear()
        products.addAll(list)
        notifyDataSetChanged()
    }
}
