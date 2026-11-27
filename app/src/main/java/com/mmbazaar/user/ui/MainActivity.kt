package com.mmbazaar.user.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.mmbazaar.user.R
import com.mmbazaar.user.data.Supabase
import com.mmbazaar.user.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Fully native Kotlin/XML customer UI. No WebView is used. */
class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private lateinit var api: Supabase
    private val products = mutableListOf<JSONObject>()
    private val categories = mutableListOf<JSONObject>()
    private val banners = mutableListOf<JSONObject>()
    private data class CartLine(val product: JSONObject, var qty: Int, val size: String, val color: String)
    private val cart = linkedMapOf<String, CartLine>()
    private val wishlist = linkedSetOf<String>()
    private var settings = JSONObject()
    private var toggles = JSONObject()
    private var categoryFilter = "all"
    private var minPrice = 0.0
    private var maxPrice = Double.MAX_VALUE
    private var onlyInStock = false
    private var sortMode = "newest"
    private val guestOrderIds = mutableListOf<String>()
    private var userData = JSONObject()
    private lateinit var productAdapter: ProductAdapter
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var flashAdapter: ProductAdapter
    private val prefs by lazy { getSharedPreferences("mmb_user_local", MODE_PRIVATE) }
    private var activeRealtime: StoreRealtime? = null
    private var appliedCoupon = ""
    private var discountPreview = 0.0
    private var walletEnabled = false
    private var currentZone = "in"
    private var selectedPayment = "cod"
    private var activeChatView: TextView? = null
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshFallback = object : Runnable { override fun run() { if (!isFinishing) { loadStore(); refreshHandler.postDelayed(this, 60_000L) } } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater); setContentView(b.root)
        api = Supabase(this); restoreLocal()
        productAdapter = ProductAdapter(mutableListOf(), { wishlist.contains(it) }, ::showProduct, ::quickAdd, ::toggleWish, { toggles.optBoolean("f_p_brand", true) }, { toggles.optBoolean("f_p_oldprice", true) })
        categoryAdapter = CategoryAdapter(mutableListOf(), ::filterCategory)
        flashAdapter = ProductAdapter(mutableListOf(), { wishlist.contains(it) }, ::showProduct, ::quickAdd, ::toggleWish, { toggles.optBoolean("f_p_brand", true) }, { toggles.optBoolean("f_p_oldprice", true) })
        b.rvFlash.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false); b.rvFlash.adapter = flashAdapter
        b.rvProducts.layoutManager = GridLayoutManager(this, 2); b.rvProducts.adapter = productAdapter
        b.rvCategories.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false); b.rvCategories.adapter = categoryAdapter
        b.swipeRefresh.setOnRefreshListener { loadStore() }
        b.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = applyFilter()
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        b.tvAccount.setOnClickListener { showProfile() }
        b.navHome.setOnClickListener { categoryFilter = "all"; b.tvProductHeading.text = "সকল পণ্য"; applyFilter() }
        b.btnFilter.setOnClickListener { showFilters() }
        b.navWishlist.setOnClickListener { if(toggles.optBoolean("f_s_wishlist",true)) showWishlist() else toast("পছন্দের তালিকা Admin থেকে বন্ধ রাখা হয়েছে") }
        b.navCart.setOnClickListener { showCart() }
        b.navOrders.setOnClickListener { showOrders() }
        b.navProfile.setOnClickListener { showProfile() }
        loadStore()
    }

    override fun onResume() { super.onResume(); startRealtime(); refreshHandler.removeCallbacks(refreshFallback); refreshHandler.postDelayed(refreshFallback, 60_000L) }
    override fun onPause() { refreshHandler.removeCallbacks(refreshFallback); activeRealtime?.close(); activeRealtime = null; super.onPause() }

    private fun restoreLocal() {
        runCatching { val a = JSONArray(prefs.getString("wishlist", "[]")); for (i in 0 until a.length()) wishlist.add(a.optString(i)) }
        runCatching { val a = JSONArray(prefs.getString("guest_orders", "[]")); for (i in 0 until a.length()) guestOrderIds.add(a.optString(i)) }
        runCatching {
            val a = JSONArray(prefs.getString("cart", "[]"))
            for (i in 0 until a.length()) { val r = a.getJSONObject(i); val p = r.getJSONObject("product"); val size = r.optString("size"); val color = r.optString("color"); cart[cartKey(p.optString("id"), size, color)] = CartLine(p, r.optInt("qty", 1), size, color) }
        }
    }
    private fun cartKey(id: String, size: String, color: String) = "$id|$size|$color"
    private fun saveLocal() {
        val a = JSONArray(); cart.values.forEach { a.put(JSONObject().put("product", it.product).put("qty", it.qty).put("size", it.size).put("color", it.color)) }
        prefs.edit().putString("cart", a.toString()).putString("wishlist", JSONArray(wishlist.toList()).toString()).putString("guest_orders", JSONArray(guestOrderIds).toString()).apply()
    }

    private fun loadStore() { lifecycleScope.launch {
        b.swipeRefresh.isRefreshing = true
        try {
            val s = api.select("settings", "select=*")
            for (i in 0 until s.length()) { val row=s.getJSONObject(i); when(row.optString("id")) { "global_config" -> settings=row.optJSONObject("data") ?: JSONObject(); "feature_toggles" -> toggles=row.optJSONObject("data") ?: JSONObject() } }
            val c=api.select("categories", "select=*&order=sort_order.asc"); categories.clear(); for(i in 0 until c.length()) if(c.getJSONObject(i).optBoolean("is_active",true)) categories.add(c.getJSONObject(i)); categoryAdapter.replace(categories)
            val ba=api.select("banners", "select=*&order=sort_order.asc"); banners.clear(); for(i in 0 until ba.length()) if(ba.getJSONObject(i).optBoolean("is_active",true)) banners.add(ba.getJSONObject(i))
            val p=api.select("products", "select=*&order=created_at.desc&limit=600"); products.clear(); for(i in 0 until p.length()) { val x=p.getJSONObject(i); if(x.optBoolean("is_active",true) && (!toggles.optBoolean("f_s_hide_out_stock",false) || availableStock(x, "", "")>0)) products.add(x) }
            val flash = products.filter { p -> p.optBoolean("is_flash_sale", false) && (p.optString("flash_ends_at").isBlank() || p.optString("flash_ends_at").take(19) > SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.US).format(Date())) }
            b.flashSection.visibility = if (flash.isNotEmpty() && toggles.optBoolean("f_s_flash_sale", true)) View.VISIBLE else View.GONE
            flashAdapter.replace(flash)
            b.tvNotice.visibility=if(toggles.optBoolean("f_s_marquee_notice",true)) View.VISIBLE else View.GONE
            b.tvNotice.text=if(settings.optBoolean("maintenance",false)) "দোকানটি সাময়িক রক্ষণাবেক্ষণে আছে" else settings.optString("marquee", "MM BAZAAR — আপনার বিশ্বস্ত অনলাইন শপ")
            val bannersOn=toggles.optBoolean("f_s_banners",true) && banners.isNotEmpty()
            b.ivBanner.visibility=if(bannersOn) View.VISIBLE else View.GONE
            b.tvBanner.visibility=if(bannersOn) View.VISIBLE else View.GONE
            b.tvBanner.text=banners.firstOrNull()?.optString("title")?.ifBlank { settings.optString("store_name","MM BAZAAR") } ?: settings.optString("store_name","MM BAZAAR")
            if(bannersOn) banners.firstOrNull()?.optString("image_url")?.takeIf { it.isNotBlank() }?.let { b.ivBanner.load(it) { crossfade(true) } }
            if(api.userId!=null) refreshUserData()
            applyFilter()
        } catch(e:Exception) { toast("ডেটা লোড হয়নি: ${e.message}") } finally { b.swipeRefresh.isRefreshing=false }
    } }

    private fun startRealtime() {
        activeRealtime?.close()
        activeRealtime = StoreRealtime(api) { runOnUiThread { loadStore(); val v=activeChatView; val uid=api.userId; if(v!=null && uid!=null) loadChat(uid,v) } }.also { it.connect() }
    }
    private fun refreshUserData() { lifecycleScope.launch { runCatching { val u=api.select("users","select=*&id=eq.${api.userId}"); if(u.length()>0) userData=u.getJSONObject(0) } } }

    private fun applyFilter() {
        val q=b.etSearch.text?.toString()?.trim()?.lowercase().orEmpty()
        val list=products.filter { p -> val cat=categories.firstOrNull { it.optString("id")==p.optString("category_id") }?.optString("name").orEmpty(); val price=p.optDouble("price"); (categoryFilter=="all" || p.optString("category_id")==categoryFilter) && (q.isBlank() || listOf(p.optString("title"),p.optString("brand"),p.optString("description"),cat).any{it.lowercase().contains(q)}) && price>=minPrice && price<=maxPrice && (!onlyInStock || availableStock(p,"","")>0) }.toMutableList()
        when(sortMode){"price_asc"->list.sortBy{it.optDouble("price")};"price_desc"->list.sortByDescending{it.optDouble("price")};else->list.sortByDescending{it.optString("created_at")}}
        productAdapter.replace(list); b.tvProductCount.text="${list.size} টি পণ্য"; b.tvEmpty.visibility=if(list.isEmpty()) View.VISIBLE else View.GONE
    }
    private fun showFilters() {
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,8,20,0)}
        val min=EditText(this).apply{hint="সর্বনিম্ন দাম";inputType=InputType.TYPE_CLASS_NUMBER;setText(if(minPrice>0)minPrice.toInt().toString() else "")}
        val max=EditText(this).apply{hint="সর্বোচ্চ দাম";inputType=InputType.TYPE_CLASS_NUMBER;setText(if(maxPrice<Double.MAX_VALUE)maxPrice.toInt().toString() else "")}
        val stock=CheckBox(this).apply{text="শুধু স্টকে থাকা পণ্য";isChecked=onlyInStock}
        val sort=Spinner(this).apply{adapter=spinnerAdapter(listOf("নতুন আগে","দাম কম থেকে বেশি","দাম বেশি থেকে কম"));setSelection(when(sortMode){"price_asc"->1;"price_desc"->2;else->0})}
        box.addView(min);box.addView(max);box.addView(stock);box.addView(label("সাজান"));box.addView(sort)
        AlertDialog.Builder(this).setTitle("পণ্য ফিল্টার").setView(box).setPositiveButton("প্রয়োগ করুন"){_,_->minPrice=min.text.toString().toDoubleOrNull()?.coerceAtLeast(0.0)?:0.0;maxPrice=max.text.toString().toDoubleOrNull()?.takeIf{it>0}?:Double.MAX_VALUE;onlyInStock=stock.isChecked;sortMode=when(sort.selectedItemPosition){1->"price_asc";2->"price_desc";else->"newest"};applyFilter()}.setNeutralButton("রিসেট"){_,_->minPrice=0.0;maxPrice=Double.MAX_VALUE;onlyInStock=false;sortMode="newest";applyFilter()}.setNegativeButton("বন্ধ",null).show()
    }
    private fun filterCategory(id:String,name:String) { categoryFilter=id; b.tvProductHeading.text=if(id=="all") "সকল পণ্য" else name; applyFilter() }
    private fun hasVariantStock(p:JSONObject)=p.optJSONObject("variant_stock")!=null
    private fun availableStock(p:JSONObject,size:String,color:String):Int {
        val total=p.optInt("stock",0).coerceAtLeast(0); val vs=p.optJSONObject("variant_stock") ?: return total
        listOf("$size|$color","$size|","|$color","|").forEach { key -> if(vs.has(key)) return vs.optInt(key,total).coerceAtLeast(0) }
        return total
    }
    private fun addLine(p:JSONObject,size:String,color:String,qty:Int=1) {
        val left=availableStock(p,size,color); val key=cartKey(p.optString("id"),size,color); val old=cart[key]?.qty ?: 0
        if(left<=old) { toast("এই ভ্যারিয়েন্টের পর্যাপ্ত স্টক নেই"); return }
        val line=cart[key]; if(line==null) cart[key]=CartLine(p,qty,size,color) else line.qty=(line.qty+qty).coerceAtMost(left)
        saveLocal(); toast("কার্টে যোগ হয়েছে")
    }
    private fun quickAdd(p:JSONObject) = showProduct(p)
    private fun toggleWish(p:JSONObject) { if(!toggles.optBoolean("f_s_wishlist",true)){toast("পছন্দের তালিকা Admin থেকে বন্ধ রাখা হয়েছে");return}; val id=p.optString("id"); if(!wishlist.add(id)) wishlist.remove(id); saveLocal(); toast(if(wishlist.contains(id)) "পছন্দের তালিকায় যোগ হয়েছে" else "পছন্দের তালিকা থেকে সরানো হয়েছে") }
    private fun showWishlist() { productAdapter.replace(products.filter{wishlist.contains(it.optString("id"))}); b.tvProductHeading.text="পছন্দের তালিকা"; b.tvProductCount.text="${wishlist.size} টি পণ্য"; b.tvEmpty.visibility=if(wishlist.isEmpty()) View.VISIBLE else View.GONE }

    private fun showProduct(p:JSONObject) {
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(22,12,22,8) }
        val image=ImageView(this).apply { layoutParams=LinearLayout.LayoutParams(-1,190); scaleType=ImageView.ScaleType.CENTER_CROP; load(p.optString("image_url")) }
        val title=TextView(this).apply { text=p.optString("title"); textSize=19f; setTextColor(getColor(R.color.text_main)); setPadding(0,10,0,4) }
        val price=TextView(this).apply { text="৳ ${money(p.optDouble("price"))}"; textSize=20f; setTextColor(getColor(R.color.gold_dark)) }
        val desc=TextView(this).apply { text=p.optString("description","বিবরণ নেই"); textSize=14f; setTextColor(getColor(R.color.text_secondary)); setPadding(0,8,0,8) }
        box.addView(image); box.addView(title); box.addView(price)
        val sizes=stringArray(p.optJSONArray("sizes")); val colors=stringArray(p.optJSONArray("colors"))
        val sizeSpin=Spinner(this); val colorSpin=Spinner(this)
        if(sizes.isNotEmpty() && toggles.optBoolean("f_p_size",true)) { box.addView(label("সাইজ নির্বাচন করুন")); sizeSpin.adapter=spinnerAdapter(sizes); box.addView(sizeSpin) }
        if(colors.isNotEmpty() && toggles.optBoolean("f_p_color",true)) { box.addView(label("কালার নির্বাচন করুন")); colorSpin.adapter=spinnerAdapter(colors); box.addView(colorSpin) }
        val stock=TextView(this).apply { setPadding(0,8,0,8) }; box.addView(stock)
        fun refreshStock() { val sz=if(sizeSpin.parent!=null && sizes.isNotEmpty() && toggles.optBoolean("f_p_size",true)) sizeSpin.selectedItem?.toString().orEmpty() else ""; val co=if(colorSpin.parent!=null && colors.isNotEmpty() && toggles.optBoolean("f_p_color",true)) colorSpin.selectedItem?.toString().orEmpty() else ""; val n=availableStock(p,sz,co); stock.text=if(n>0) "এই ভ্যারিয়েন্টে স্টক: $n" else "এই ভ্যারিয়েন্টের স্টক শেষ"; stock.setTextColor(getColor(if(n>0) R.color.success else R.color.danger)) }
        sizeSpin.onItemSelectedListener=spinnerListener { refreshStock() }; colorSpin.onItemSelectedListener=spinnerListener { refreshStock() }; refreshStock(); box.addView(desc)
        val reviews=TextView(this).apply { text="রিভিউ লোড হচ্ছে…"; setPadding(0,8,0,4); textSize=13f }; box.addView(reviews)
        lifecycleScope.launch { runCatching { val r=api.select("reviews","select=*&product_id=eq.${p.optString("id")}&order=created_at.desc&limit=10"); reviews.text=if(r.length()==0) "এখনো কোনো রিভিউ নেই" else (0 until r.length()).joinToString("\n\n") { i -> val x=r.getJSONObject(i); "${x.optString("user_name","গ্রাহক")}  ${"★".repeat(x.optInt("rating").coerceIn(0,5))}\n${x.optString("text")}\n${x.optString("reply").takeIf{it.isNotBlank()}?.let{"দোকানের উত্তর: $it"}.orEmpty()}" } } }
        val d=AlertDialog.Builder(this).setTitle("পণ্যের বিবরণ").setView(ScrollView(this).apply{addView(box)}).setPositiveButton("কার্টে যোগ করুন",null).setNeutralButton("রিভিউ লিখুন",null).setNegativeButton("বন্ধ",null).create()
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { val sz=if(sizes.isNotEmpty() && toggles.optBoolean("f_p_size",true)) sizeSpin.selectedItem?.toString().orEmpty() else ""; val co=if(colors.isNotEmpty() && toggles.optBoolean("f_p_color",true)) colorSpin.selectedItem?.toString().orEmpty() else ""; addLine(p,sz,co); if(availableStock(p,sz,co)>0) d.dismiss() }; d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { d.dismiss(); openReview(p) } }
        d.show()
    }

    private fun showCart() {
        if(cart.isEmpty()) { AlertDialog.Builder(this).setMessage("আপনার কার্ট খালি।").setPositiveButton("ঠিক আছে",null).show(); return }
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,8,18,8)}
        cart.values.toList().forEach { line ->
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            val info=TextView(this).apply{text="${line.product.optString("title")}\n${if(line.size.isNotBlank()) "সাইজ: ${line.size}  " else ""}${if(line.color.isNotBlank()) "কালার: ${line.color}" else ""}\n৳${money(line.product.optDouble("price")*line.qty)}";layoutParams=LinearLayout.LayoutParams(0,-2,1f);setTextColor(getColor(R.color.text_main));setPadding(0,10,0,10)}
            val minus=Button(this).apply{text="−";setOnClickListener{line.qty--;if(line.qty<=0)cart.remove(cartKey(line.product.optString("id"),line.size,line.color));saveLocal();showCart()}}
            val count=TextView(this).apply{text=line.qty.toString();setPadding(8,0,8,0)}
            val plus=Button(this).apply{text="+";setOnClickListener{if(line.qty<availableStock(line.product,line.size,line.color)){line.qty++;saveLocal();showCart()}else toast("স্টক সীমা অতিক্রম করা যাবে না")}}
            row.addView(info);row.addView(minus);row.addView(count);row.addView(plus);box.addView(row);box.addView(View(this).apply{setBackgroundColor(0xFFE5E7EB.toInt());layoutParams=LinearLayout.LayoutParams(-1,1)})
        }
        val subtotal=subtotal(); box.addView(label("পণ্যমূল্য: ৳${money(subtotal)}"))
        AlertDialog.Builder(this).setTitle("আপনার কার্ট (${cart.size})").setView(ScrollView(this).apply{addView(box)}).setPositiveButton("চেকআউট"){_,_->showCheckout()}.setNegativeButton("বন্ধ",null).show()
    }
    private fun subtotal()=cart.values.sumOf{it.product.optDouble("price")*it.qty}
    private fun couponDiscount(code:String, sub:Double, c:JSONObject):Double { var d=if(c.optString("type")=="percent") (sub*c.optDouble("value")/100.0).roundToInt().toDouble() else c.optDouble("value"); if(c.has("max_discount")&&!c.isNull("max_discount")) d=minOf(d,c.optDouble("max_discount")); return d.coerceIn(0.0,sub) }
    private fun totals():Triple<Double,Double,Double> { val sub=subtotal(); val discount=if(appliedCoupon.isBlank())0.0 else 0.0; val after=(sub-discount).coerceAtLeast(0.0); val free=settings.optDouble("free_delivery_threshold",0.0)>0&&after>=settings.optDouble("free_delivery_threshold",0.0); val delivery=if(free)0.0 else settings.optDouble(if(currentZone=="in")"del_in" else "del_out",if(currentZone=="in")60.0 else 120.0); val tax=(after*settings.optDouble("tax",0.0)/100.0).roundToInt().toDouble(); return Triple(delivery,tax,after+delivery+tax) }

    private fun showCheckout() {
        if(cart.isEmpty())return
        if(!toggles.optBoolean("f_s_guest_checkout",true)&&api.userId==null){showAuth();return}
        val form=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,8,20,4)}
        fun field(h:String,type:Int=InputType.TYPE_CLASS_TEXT):EditText=EditText(this).apply{hint=h;inputType=type;form.addView(this)}
        val name=field("আপনার নাম");val phone=field("মোবাইল নম্বর (01XXXXXXXXX)",InputType.TYPE_CLASS_PHONE);val address=field("সম্পূর্ণ ঠিকানা");val notes=field("অর্ডার নোট (ঐচ্ছিক)");val coupon=field("কুপন কোড (ঐচ্ছিক)")
        val applyCouponButton=Button(this).apply{text="কুপন প্রয়োগ করুন"};form.addView(applyCouponButton)
        if(api.userId!=null){name.setText(userData.optString("name"));phone.setText(userData.optString("phone"));address.setText(userData.optString("address"))}
        val zone=Spinner(this);zone.adapter=spinnerAdapter(listOf("ঢাকার ভিতরে","ঢাকার বাইরে"));form.addView(label("ডেলিভারি এলাকা"));form.addView(zone)
        val payMethods=mutableListOf<Pair<String,String>>();if(settings.optBoolean("cod_on",true))payMethods.add("cod" to "ক্যাশ অন ডেলিভারি");if(settings.optBoolean("bkash_on",true))payMethods.add("bkash" to "বিকাশ");if(settings.optBoolean("nagad_on",true))payMethods.add("nagad" to "নগদ");if(payMethods.isEmpty())payMethods.add("cod" to "ক্যাশ অন ডেলিভারি")
        val pay=Spinner(this);pay.adapter=spinnerAdapter(payMethods.map{it.second});form.addView(label("পেমেন্ট পদ্ধতি"));form.addView(pay)
        val trx=field("বিকাশ/নগদ TrxID (অনলাইন পেমেন্টে)")
        val wallet=CheckBox(this).apply{text="ওয়ালেট ব্যবহার করুন (৳${money(userData.optDouble("wallet_balance"))})";isEnabled=api.userId!=null&&userData.optDouble("wallet_balance")>0};form.addView(wallet)
        val summary=TextView(this).apply{setPadding(0,8,0,8);setTextColor(getColor(R.color.gold_dark))};form.addView(summary)
        val waAvailable=toggles.optBoolean("f_s_wa_checkout",true)&&settings.optString("whatsapp",settings.optString("phone")).filter{it.isDigit()}.length>=10
        if(waAvailable)form.addView(TextView(this).apply{text="WhatsApp-এ অর্ডার পাঠানো যাবে";setTextColor(getColor(R.color.success));setPadding(0,4,0,4)})
        fun updateSummary(){currentZone=if(zone.selectedItemPosition==0)"in" else "out";selectedPayment=payMethods[pay.selectedItemPosition.coerceIn(0,payMethods.lastIndex)].first;walletEnabled=wallet.isChecked;val after=(subtotal()-discountPreview).coerceAtLeast(0.0);val free=settings.optDouble("free_delivery_threshold",0.0)>0&&after>=settings.optDouble("free_delivery_threshold",0.0);val delivery=if(free)0.0 else settings.optDouble(if(currentZone=="in")"del_in" else "del_out",if(currentZone=="in")60.0 else 120.0);val tax=(after*settings.optDouble("tax",0.0)/100.0).roundToInt().toDouble();val total=after+delivery+tax;val w=if(walletEnabled)minOf(userData.optDouble("wallet_balance"),total)else 0.0;summary.text="সাবটোটাল: ৳${money(subtotal())}\nকুপন ছাড়: -৳${money(discountPreview)}\nডেলিভারি: ৳${money(delivery)}\nট্যাক্স: ৳${money(tax)}\nসর্বমোট: ৳${money(total)}\nওয়ালেট: -৳${money(w)}\nপরিশোধযোগ্য: ৳${money(total-w)}\n${if(selectedPayment=="bkash")"বিকাশ নম্বর: ${settings.optString("bkash")}" else if(selectedPayment=="nagad")"নগদ নম্বর: ${settings.optString("nagad")}" else ""}"}
        zone.onItemSelectedListener=spinnerListener{updateSummary()};pay.onItemSelectedListener=spinnerListener{updateSummary()};wallet.setOnCheckedChangeListener{_,_->updateSummary()};applyCouponButton.setOnClickListener{val code=coupon.text.toString().trim().uppercase();if(code.isBlank()){toast("কুপন কোড লিখুন")}else lifecycleScope.launch{runCatching{val a=api.select("coupons","select=*&code=eq.${Uri.encode(code)}&is_active=eq.true");if(a.length()==0)throw IllegalArgumentException("অবৈধ কুপন কোড");val c=a.getJSONObject(0);if(c.optString("expires_at").isNotBlank()&&c.optString("expires_at").take(19)<SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.US).format(Date()))throw IllegalArgumentException("কুপনের মেয়াদ শেষ");if(c.optInt("usage_limit",Int.MAX_VALUE)<=c.optInt("used_count",0))throw IllegalArgumentException("কুপনের ব্যবহার সীমা শেষ");if(subtotal()<c.optDouble("min_order",0.0))throw IllegalArgumentException("ন্যূনতম অর্ডার ৳${money(c.optDouble("min_order"))}");discountPreview=couponDiscount(code,subtotal(),c);appliedCoupon=code;updateSummary();toast("কুপন প্রয়োগ হয়েছে")} .onFailure{discountPreview=0.0;appliedCoupon="";updateSummary();toast(it.message?:"কুপন যাচাই ব্যর্থ")}}};updateSummary()
        val d=AlertDialog.Builder(this).setTitle("অর্ডার নিশ্চিত করুন").setView(ScrollView(this).apply{addView(form)}).setPositiveButton("অর্ডার নিশ্চিত করুন",null).setNeutralButton(if(waAvailable)"WhatsApp" else "",null).setNegativeButton("বাতিল",null).create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { val nm=name.text.toString().trim();val ph=phone.text.toString().filter{it.isDigit()};val ad=address.text.toString().trim();if(nm.isBlank()||!Regex("^01[3-9][0-9]{8}$").matches(ph)||ad.isBlank()){toast("নাম, সঠিক ১১ ডিজিটের নম্বর ও ঠিকানা দিন");return@setOnClickListener};if(selectedPayment!="cod"&&trx.text.isNullOrBlank()){toast("TrxID দিন");return@setOnClickListener};lifecycleScope.launch{placeOrder(nm,ph,ad,notes.text.toString(),currentZone,selectedPayment,trx.text.toString(),coupon.text.toString().trim().uppercase(),wallet.isChecked,d)} }
            if(waAvailable)d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener{val nm=name.text.toString().trim();val ph=phone.text.toString().filter{it.isDigit()};val ad=address.text.toString().trim();if(nm.isBlank()||!Regex("^01[3-9][0-9]{8}$").matches(ph)||ad.isBlank()){toast("নাম, সঠিক ফোন ও ঠিকানা দিন");return@setOnClickListener};openWhatsApp(nm,ph,ad,notes.text.toString());d.dismiss()}
        };d.show()
    }

    private suspend fun placeOrder(name:String,phone:String,address:String,notes:String,zone:String,method:String,trx:String,couponCode:String,useWallet:Boolean,dialog:AlertDialog) {
        try {
            if(api.userId!=null&&userData.optBoolean("is_blocked",false)){toast("আপনার অ্যাকাউন্ট ব্লক করা হয়েছে");return}
            val sub=subtotal();var discount=0.0
            if(couponCode.isNotBlank()){val cs=api.select("coupons","select=*&code=eq.${Uri.encode(couponCode)}&is_active=eq.true");if(cs.length()==0){toast("কুপন সঠিক নয় বা বন্ধ");return};val c=cs.getJSONObject(0);if(sub<c.optDouble("min_order",0.0)){toast("এই কুপনের ন্যূনতম অর্ডার ৳${money(c.optDouble("min_order"))}");return};if(c.optInt("usage_limit",Int.MAX_VALUE)<=c.optInt("used_count",0)){toast("কুপনের ব্যবহার সীমা শেষ");return};val exp=c.optString("expires_at");if(exp.isNotBlank()&&exp.take(19)<SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.US).format(Date())){toast("কুপনের মেয়াদ শেষ");return};discount=couponDiscount(couponCode,sub,c)}
            val after=(sub-discount).coerceAtLeast(0.0);val free=settings.optDouble("free_delivery_threshold",0.0)>0&&after>=settings.optDouble("free_delivery_threshold",0.0);val del=if(free)0.0 else settings.optDouble(if(zone=="in")"del_in" else "del_out",if(zone=="in")60.0 else 120.0);val tax=(after*settings.optDouble("tax",0.0)/100.0).roundToInt().toDouble();val total=after+del+tax;val walletUsed=if(useWallet&&api.userId!=null)minOf(userData.optDouble("wallet_balance"),total)else 0.0
            if(total<settings.optDouble("min_order",0.0)){toast("ন্যূনতম অর্ডার ৳${money(settings.optDouble("min_order"))}");return}
            val items=JSONArray();cart.values.forEach{items.put(JSONObject().put("id",it.product.optString("id")).put("qty",it.qty).put("selectedSize",it.size).put("selectedColor",it.color))}
            val payload=JSONObject().put("p_customer_name",name).put("p_phone",phone).put("p_address",address).put("p_notes",notes).put("p_delivery_zone",zone).put("p_delivery_charge",del).put("p_tax",tax).put("p_wallet_used",walletUsed).put("p_payment_method",method).put("p_trx_id",trx).put("p_coupon_code",couponCode).put("p_items",items)
            val result=api.rpc("place_order_atomic",payload);val orderId=result.trim().trim('"')
            if(api.userId==null&&orderId.isNotBlank())guestOrderIds.add(orderId)
            cart.clear();appliedCoupon="";discountPreview=0.0;walletEnabled=false;saveLocal();dialog.dismiss();if(api.userId!=null)refreshUserData()
            AlertDialog.Builder(this).setTitle("অর্ডার সফল হয়েছে").setMessage("আপনার অর্ডার গ্রহণ করা হয়েছে।\nঅর্ডার আইডি: ${orderId.take(18)}").setPositiveButton("আমার অর্ডার"){_,_->showOrders()}.setNegativeButton("ঠিক আছে",null).show()
        }catch(e:Exception){toast("অর্ডার করা যায়নি: ${e.message}")}
    }

    private fun openWhatsApp(name:String,phone:String,address:String,notes:String) {
        val number=settings.optString("whatsapp").ifBlank{settings.optString("phone")}.filter{it.isDigit()};if(number.isBlank()){toast("WhatsApp নম্বর সেট করা নেই");return}
        val sub=subtotal();val zoneDel=settings.optDouble(if(currentZone=="in")"del_in" else "del_out",if(currentZone=="in")60.0 else 120.0);val tax=(sub*settings.optDouble("tax",0.0)/100.0).roundToInt().toDouble()
        val lines=cart.values.mapIndexed{i,l->"${i+1}. ${l.product.optString("title")}${if(l.size.isNotBlank())" | সাইজ: ${l.size}" else ""}${if(l.color.isNotBlank())" | কালার: ${l.color}" else ""} × ${l.qty} = ৳${money(l.product.optDouble("price")*l.qty)}"}
        val msg=(listOf("নতুন অর্ডার — ${settings.optString("store_name","MM BAZAAR")}","")+lines+listOf("","সাবটোটাল: ৳${money(sub)}","ডেলিভারি: ৳${money(zoneDel)}","ট্যাক্স: ৳${money(tax)}","সর্বমোট: ৳${money(sub+zoneDel+tax)}","","নাম: $name","মোবাইল: $phone","ঠিকানা: $address",if(notes.isNotBlank())"নোট: $notes" else "")).filter{it.isNotBlank()}.joinToString("\n")
        val uri=Uri.parse("https://wa.me/88${number.removePrefix("88")}?text=${URLEncoder.encode(msg,"UTF-8")}");runCatching{startActivity(Intent(Intent.ACTION_VIEW,uri))}.onFailure{toast("WhatsApp খোলা যায়নি")}
    }

    private fun showOrders() { lifecycleScope.launch { try { val rows=if(api.userId!=null)api.select("orders","select=*&user_id=eq.${api.userId}&order=created_at.desc") else if(guestOrderIds.isNotEmpty())JSONArray(api.rpc("get_guest_orders_by_ids",JSONObject().put("p_ids",JSONArray(guestOrderIds))))else JSONArray();val text=(0 until rows.length()).joinToString("\n\n"){i->val o=rows.getJSONObject(i);"#${o.optString("id").take(8)} · ${o.optString("status")}\nমোট: ৳${money(o.optDouble("total_amount"))}\n${o.optString("created_at")}"};AlertDialog.Builder(this@MainActivity).setTitle("আমার অর্ডার").setMessage(text.ifBlank{"কোনো অর্ডার পাওয়া যায়নি"}).setPositiveButton("ঠিক আছে",null).setNeutralButton("ফোন দিয়ে ট্র্যাক"){_,_->trackByPhone()}.setNegativeButton("অর্ডার বাতিল"){_,_->cancelOrderPrompt()}.show() }catch(e:Exception){toast("অর্ডার লোড হয়নি: ${e.message}")} } }
    private fun cancelOrderPrompt(){val input=EditText(this).apply{hint="সম্পূর্ণ Order ID"};AlertDialog.Builder(this).setTitle("অর্ডার বাতিল").setMessage("শুধু Pending অর্ডার বাতিল করা যাবে").setView(input).setPositiveButton("বাতিল নিশ্চিত"){_,_->lifecycleScope.launch{runCatching{api.rpc("cancel_order_atomic",JSONObject().put("p_order_id",input.text.toString().trim()));toast("বাতিলের অনুরোধ সফল")} .onFailure{toast("বাতিল হয়নি: ${it.message}")}}}.setNegativeButton("ফিরে যান",null).show()}
    private fun trackByPhone(){val input=EditText(this).apply{hint="01XXXXXXXXX";inputType=InputType.TYPE_CLASS_PHONE};AlertDialog.Builder(this).setTitle("অর্ডার ট্র্যাক করুন").setView(input).setPositiveButton("খুঁজুন"){_,_->lifecycleScope.launch{runCatching{val a=JSONArray(api.rpc("track_orders_by_phone",JSONObject().put("p_phone",input.text.toString())));val msg=(0 until a.length()).joinToString("\n\n"){i->val o=a.getJSONObject(i);"#${o.optString("id").take(8)} · ${o.optString("status")} · ৳${money(o.optDouble("total_amount"))}"};AlertDialog.Builder(this@MainActivity).setMessage(msg.ifBlank{"কোনো অর্ডার পাওয়া যায়নি"}).setPositiveButton("ঠিক আছে",null).show()}.onFailure{toast("ট্র্যাক করা যায়নি: ${it.message}")}}}.setNegativeButton("বাতিল",null).show()}

    private fun showProfile(){if(api.userId==null){showAuth();return};lifecycleScope.launch{runCatching{val u=api.select("users","select=*&id=eq.${api.userId}");if(u.length()>0)userData=u.getJSONObject(0)};val name=userData.optString("name",api.email?:"আমার অ্যাকাউন্ট");val message="ইমেইল: ${api.email}\nমোবাইল: ${userData.optString("phone")}\nঠিকানা: ${userData.optString("address")}\n\nওয়ালেট: ৳${money(userData.optDouble("wallet_balance"))}\nLoyalty Points: ${userData.optInt("loyalty_points")}";AlertDialog.Builder(this@MainActivity).setTitle(name).setMessage(message).setPositiveButton("লগআউট"){_,_->api.signOut();userData=JSONObject();toast("লগআউট হয়েছে")}.setNeutralButton("প্রোফাইল সম্পাদনা"){_,_->editProfile()}.setNegativeButton("কাস্টমার চ্যাট",null).create().also{dialog->dialog.setOnDismissListener{};dialog.show();dialog.setOnKeyListener{_,_,_->false};dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener{dialog.dismiss();openChat()}}}}
    private fun editProfile(){val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,8,20,0)};val name=EditText(this).apply{hint="নাম";setText(userData.optString("name"))};val phone=EditText(this).apply{hint="মোবাইল";inputType=InputType.TYPE_CLASS_PHONE;setText(userData.optString("phone"))};val address=EditText(this).apply{hint="ঠিকানা";minLines=2;setText(userData.optString("address"))};box.addView(name);box.addView(phone);box.addView(address);val d=AlertDialog.Builder(this).setTitle("প্রোফাইল আপডেট").setView(box).setPositiveButton("সংরক্ষণ",null).setNegativeButton("বাতিল",null).create();d.setOnShowListener{d.getButton(-1).setOnClickListener{val ph=phone.text.toString().filter{it.isDigit()};if(!Regex("^01[3-9][0-9]{8}$").matches(ph)){toast("সঠিক ১১ ডিজিটের ফোন দিন");return@setOnClickListener};lifecycleScope.launch{runCatching{api.update("users","id=eq.${api.userId}",JSONObject().put("name",name.text.toString().trim()).put("phone",ph).put("address",address.text.toString().trim()));userData.put("name",name.text.toString().trim()).put("phone",ph).put("address",address.text.toString().trim());d.dismiss();toast("প্রোফাইল আপডেট হয়েছে")}.onFailure{toast("আপডেট হয়নি: ${it.message}")}}}};d.show()}
    private fun showAuth(){val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,8,20,0)};val name=EditText(this).apply{hint="নাম (নতুন অ্যাকাউন্ট)"};val phone=EditText(this).apply{hint="মোবাইল (নতুন অ্যাকাউন্ট)";inputType=InputType.TYPE_CLASS_PHONE};val email=EditText(this).apply{hint="ইমেইল";inputType=33};val pass=EditText(this).apply{hint="পাসওয়ার্ড (কমপক্ষে ৬ অক্ষর)";inputType=129};val forgot=TextView(this).apply{text="পাসওয়ার্ড ভুলে গেছেন?";setTextColor(getColor(R.color.gold_dark));setPadding(0,12,0,4);setOnClickListener{val e=email.text.toString().trim();if(e.isBlank())toast("আগে ইমেইল লিখুন")else lifecycleScope.launch{runCatching{api.resetPasswordForEmail(e);toast("পাসওয়ার্ড রিসেট লিংক পাঠানো হয়েছে")}.onFailure{toast(it.message?:"ব্যর্থ")}}}};box.addView(name);box.addView(phone);box.addView(email);box.addView(pass);box.addView(forgot);val d=AlertDialog.Builder(this).setTitle("লগইন / রেজিস্ট্রেশন").setView(box).setPositiveButton("লগইন",null).setNeutralButton("নতুন অ্যাকাউন্ট",null).setNegativeButton("বন্ধ",null).create();d.setOnShowListener{d.getButton(-1).setOnClickListener{doAuth(email.text.toString(),pass.text.toString(),name.text.toString(),phone.text.toString(),false,d)};d.getButton(-3).setOnClickListener{doAuth(email.text.toString(),pass.text.toString(),name.text.toString(),phone.text.toString(),true,d)}};d.show()}
    private fun doAuth(email:String,password:String,name:String,phone:String,signup:Boolean,d:AlertDialog){lifecycleScope.launch{runCatching{if(signup){if(name.isBlank()||!Regex("^01[3-9][0-9]{8}$").matches(phone.filter{it.isDigit()}))throw IllegalArgumentException("নাম ও সঠিক ১১ ডিজিটের ফোন দিন");if(password.length<6)throw IllegalArgumentException("পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে");api.signUp(email.trim(),password,name.trim(),phone.filter{it.isDigit()})}else api.signIn(email.trim(),password);d.dismiss();refreshUserData();toast(if(signup)"অ্যাকাউন্ট তৈরি হয়েছে" else "লগইন সফল");showProfile()}.onFailure{toast(it.message?:"লগইন ব্যর্থ")}}}
    private fun openReview(p:JSONObject){if(api.userId==null){showAuth();return};val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,8,20,0)};val rating=EditText(this).apply{hint="রেটিং ১-৫";inputType=2;setText("5")};val text=EditText(this).apply{hint="আপনার মন্তব্য";minLines=3};box.addView(rating);box.addView(text);AlertDialog.Builder(this).setTitle("রিভিউ লিখুন").setView(box).setPositiveButton("জমা দিন"){_,_->lifecycleScope.launch{runCatching{api.rpc("submit_review_atomic",JSONObject().put("p_product_id",p.optString("id")).put("p_user_name",userData.optString("name","গ্রাহক")).put("p_rating",rating.text.toString().toDoubleOrNull()?.coerceIn(1.0,5.0)?:5.0).put("p_text",text.text.toString()));toast("রিভিউ জমা হয়েছে")}.onFailure{toast("রিভিউ হয়নি: ${it.message}")}}}.setNegativeButton("বাতিল",null).show()}
    private fun openChat(){if(api.userId==null){showAuth();return};val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,8,16,0)};val messages=TextView(this).apply{minHeight=180;setTextColor(getColor(R.color.text_main));text="চ্যাট লোড হচ্ছে…"};val input=EditText(this).apply{hint="মেসেজ লিখুন"};box.addView(ScrollView(this).apply{addView(messages)});box.addView(input);val d=AlertDialog.Builder(this).setTitle("কাস্টমার সাপোর্ট").setView(box).setPositiveButton("পাঠান",null).setNegativeButton("বন্ধ",null).create();d.setOnShowListener{d.getButton(-1).setOnClickListener{val msg=input.text.toString().trim();if(msg.isNotBlank())lifecycleScope.launch{runCatching{val uid=api.userId!!;val now=nowIso();api.upsert("chats",JSONObject().put("id",uid).put("user_id",uid).put("user_name",userData.optString("name",api.email)).put("phone",userData.optString("phone")).put("email",api.email).put("last_message",msg).put("unread_for_admin",true).put("unread_for_user",false).put("updated_at",now));api.insert("chat_messages",JSONObject().put("chat_id",uid).put("sender_id",uid).put("text",msg).put("timestamp",now));input.text.clear();loadChat(uid,messages)}.onFailure{toast("মেসেজ পাঠানো যায়নি: ${it.message}")}}}};d.setOnDismissListener { activeChatView=null }; d.show(); activeChatView=messages; loadChat(api.userId!!,messages)}
    private fun loadChat(uid:String,target:TextView){lifecycleScope.launch{runCatching{val a=api.select("chat_messages","select=*&chat_id=eq.$uid&order=timestamp.asc");target.text=(0 until a.length()).joinToString("\n\n"){i->val m=a.getJSONObject(i);(if(m.optString("sender_id")==uid)"আপনি: " else "সাপোর্ট: ")+m.optString("text")}.ifBlank{"এখনো কোনো মেসেজ নেই"}}}}

    private fun stringArray(a:JSONArray?):List<String> = if(a==null) emptyList() else (0 until a.length()).map{a.optString(it)}.filter{it.isNotBlank()}
    private fun spinnerAdapter(items:List<String>)=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,items)
    private fun label(s:String)=TextView(this).apply{text=s;textSize=13f;setTextColor(getColor(R.color.text_secondary));setPadding(0,8,0,2)}
    private fun money(n:Double)=String.format(Locale.US,"%,.0f",n)
    private fun nowIso()=SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US).format(Date())
    private fun spinnerListener(action:()->Unit)=object:AdapterView.OnItemSelectedListener{override fun onNothingSelected(parent:AdapterView<*>?){};override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long)=action()}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}
