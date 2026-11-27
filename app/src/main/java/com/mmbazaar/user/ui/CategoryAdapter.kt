package com.mmbazaar.user.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mmbazaar.user.databinding.ItemCategoryBinding
import org.json.JSONObject

class CategoryAdapter(private val rows: MutableList<JSONObject>, private val onSelect: (String, String) -> Unit) : RecyclerView.Adapter<CategoryAdapter.Holder>() {
    inner class Holder(val b: ItemCategoryBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = rows.size
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val c = rows[position]; holder.b.tvCategory.text = c.optString("name", "সব")
        holder.b.tvCategory.setOnClickListener { onSelect(c.optString("id"), c.optString("name")) }
    }
    fun replace(list: List<JSONObject>) { rows.clear(); rows.add(JSONObject().put("id", "all").put("name", "সব")); rows.addAll(list); notifyDataSetChanged() }
}
