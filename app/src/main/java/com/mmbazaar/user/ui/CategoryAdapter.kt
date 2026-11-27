package com.mmbazaar.user.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.mmbazaar.user.R
import com.mmbazaar.user.databinding.ItemCategoryBinding
import org.json.JSONObject

class CategoryAdapter(
    private val rows: MutableList<JSONObject>,
    private val onSelect: (String, String) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.Holder>() {
    inner class Holder(val b: ItemCategoryBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val category = rows[position]
        val id = category.optString("id")
        val name = category.optString("name", "সব")
        val iconUrl = category.optString("icon_url")
        holder.b.tvCategory.text = if (iconUrl.isBlank()) (if (id == "all") "⚡ $name" else "${emojiFor(name)} $name") else name
        if (iconUrl.isNotBlank()) {
            holder.b.ivCategory.visibility = View.VISIBLE
            holder.b.ivCategory.load(iconUrl) {
                crossfade(true)
                placeholder(R.drawable.bg_card)
                error(R.drawable.bg_card)
            }
        } else {
            holder.b.ivCategory.visibility = View.GONE
            holder.b.ivCategory.setImageDrawable(null)
        }
        holder.b.root.setOnClickListener { onSelect(id, name) }
    }

    private fun emojiFor(name: String): String {
        val value = name.lowercase()
        return when {
            listOf("shoe", "sandal", "footwear", "জুতা").any { value.contains(it) } -> "👟"
            listOf("home", "living", "furniture", "ঘর").any { value.contains(it) } -> "🏠"
            listOf("glass", "sunglass", "eye", "চশমা").any { value.contains(it) } -> "🕶️"
            listOf("perfume", "fragrance", "attar", "সুগন্ধি").any { value.contains(it) } -> "🧴"
            listOf("stationery", "pen", "book", "খাতা").any { value.contains(it) } -> "✏️"
            listOf("skin", "care", "beauty", "cream", "ত্বক", "বিউটি").any { value.contains(it) } -> "✨"
            listOf("men", "fashion", "shirt", "pant", "জামা", "ফ্যাশন").any { value.contains(it) } -> "👔"
            listOf("watch", "smart", "clock", "ঘড়ি", "ঘড়ি").any { value.contains(it) } -> "⌚"
            listOf("kitchen", "cook", "utensil", "রান্না").any { value.contains(it) } -> "🍳"
            listOf("electronic", "gadget", "mobile", "ইলেকট্রনিক্স", "মোবাইল").any { value.contains(it) } -> "📱"
            listOf("food", "grocery", "খাবার").any { value.contains(it) } -> "🍎"
            listOf("toy", "kid", "baby", "খেলনা").any { value.contains(it) } -> "🧸"
            listOf("bag", "purse", "ব্যাগ").any { value.contains(it) } -> "👜"
            listOf("jewel", "gold", "গয়না", "গয়না").any { value.contains(it) } -> "💎"
            listOf("health", "fitness", "ব্যায়াম", "ব্যায়াম").any { value.contains(it) } -> "💪"
            listOf("women", "lady", "girl", "মহিলা").any { value.contains(it) } -> "👗"
            else -> "📦"
        }
    }

    fun replace(list: List<JSONObject>) {
        rows.clear()
        rows.add(JSONObject().put("id", "all").put("name", "সব পণ্য"))
        rows.addAll(list)
        notifyDataSetChanged()
    }
}
