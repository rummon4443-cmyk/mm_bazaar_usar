package com.mmbazaar.user.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    companion object { private const val VOICE_SEARCH_REQUEST = 731 }
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
    private var filteredProducts: List<JSONObject> = emptyList()
    private var productDisplayLimit = 20
    private val productPageSize = 20
    private val guestOrderIds = mutableListOf<String>()
    private var userData = JSONObject()
    private lateinit var productAdapter: ProductAdapter
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var flashAdapter: ProductAdapter
    private val prefs by lazy { getSharedPreferences("mmb_user_local", MODE_PRIVATE) }
    private var activeRealtime: StoreRealtime? = null
    private var appliedCoupon = ""
    private var discountPreview = 0.0
    private var bannerIndex = 0
    private var initialDeepLinkHandled = false
    private var maintenanceDialog: AlertDialog? = null
    private val bannerSlideHandler = Handler(Looper.getMainLooper())
    private val bannerSlideRunnable = object : Runnable { override fun run() { if (banners.size > 1 && !isFinishing) { bannerIndex = (bannerIndex + 1) % banners.size; showCurrentBanner(); bannerSlideHandler.postDelayed(this, 4500L) } } }
    private var walletEnabled = false
    private var currentZone = "in"
    private var selectedPayment = "cod"
    private var activeChatView: TextView? = null
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val searchHandler = Handler(Looper.getMainLooper())
    private val flashHandler = Handler(Looper.getMainLooper())
    private var pendingSearchSave: Runnable? = null
    private val smartSearchSynonyms = listOf(
        listOf("ঘড়ি", "ঘড়ি", "watch", "watches", "smartwatch", "clock", "timepiece"),
        listOf("জুতা", "স্যান্ডেল", "স্নিকার", "shoe", "shoes", "sandal", "sandals", "sneaker", "footwear", "boot", "loafer"),
        listOf("চশমা", "সানগ্লাস", "glass", "glasses", "sunglass", "sunglasses", "eyewear"),
        listOf("পারফিউম", "সুগন্ধি", "আতর", "বডি স্প্রে", "perfume", "perfumes", "fragrance", "attar", "spray", "cologne"),
        listOf("ব্যাগ", "মানিব্যাগ", "পার্স", "ব্যাকপ্যাক", "bag", "bags", "purse", "backpack", "wallet", "handbag"),
        listOf("জামা", "শার্ট", "টিশার্ট", "টি-শার্ট", "গেঞ্জি", "প্যান্ট", "পাঞ্জাবি", "shirt", "t-shirt", "tshirt", "polo", "pant", "pants", "panjabi", "jeans", "men", "mens"),
        listOf("শাড়ি", "শাড়ী", "শাড়ি", "থ্রি পিস", "বোরকা", "হিজাব", "কুর্তি", "মহিলা", "মেয়ে", "মেয়ে", "women", "womens", "lady", "ladies", "saree", "kurti", "borka", "hijab", "dress"),
        listOf("বিউটি", "ক্রিম", "ত্বক", "মেকআপ", "লিপস্টিক", "ফেসওয়াশ", "beauty", "skin", "skincare", "cream", "serum", "cosmetic", "cosmetics", "makeup", "facewash"),
        listOf("গয়না", "গয়না", "গহনা", "আংটি", "চুড়ি", "চুরি", "নেকলেস", "jewel", "jewelry", "jewellery", "gold", "ring", "necklace", "bangle", "earring"),
        listOf("মোবাইল", "ফোন", "ইলেকট্রনিক্স", "হেডফোন", "এয়ারবাড", "চার্জার", "mobile", "phone", "electronic", "electronics", "gadget", "gadgets", "headphone", "earbuds", "airpods", "charger", "speaker"),
        listOf("খেলনা", "বাচ্চা", "toy", "toys", "kid", "kids", "baby"),
        listOf("খাতা", "বই", "কলম", "স্টেশনারি", "stationery", "book", "books", "pen", "notebook"),
        listOf("রান্না", "কিচেন", "বোতল", "ফ্লাস্ক", "kitchen", "cook", "cookware", "utensil", "bottle", "flask", "blender"),
        listOf("ঘর", "হোম", "লাইট", "ল্যাম্প", "চাদর", "home", "living", "decor", "furniture", "lamp", "bedsheet"),
        listOf("খাবার", "ফুড", "গ্রোসারি", "food", "grocery", "snack", "honey", "tea"),
        listOf("ব্যায়াম", "ব্যায়াম", "স্বাস্থ্য", "ফিটনেস", "health", "fitness", "gym")
    )
    private val flashTicker = object : Runnable { override fun run() { updateFlashCountdown(); if (!isFinishing) flashHandler.postDelayed(this, 1000L) } }
    private val refreshFallback = object : Runnable { override fun run() { if (!isFinishing) { loadStore(); refreshHandler.postDelayed(this, 60_000L) } } }

    override fun onCreate(savedInstanceState: Bundle?) {
        val dark = getSharedPreferences("mmb_user_local", MODE_PRIVATE).getBoolean("dark_mode", false); AppCompatDelegate.setDefaultNightMode(if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater); setContentView(b.root)
        api = Supabase(this); restoreLocal()
        productAdapter = ProductAdapter(mutableListOf(), { wishlist.contains(it) }, ::showProduct, ::quickAdd, ::toggleWish, { toggles.optBoolean("f_p_brand", true) }, { toggles.optBoolean("f_p_oldprice", true) }, ::buyNow, { toggles.optBoolean("f_s_wishlist", true) })
        categoryAdapter = CategoryAdapter(mutableListOf(), ::filterCategory)
        flashAdapter = ProductAdapter(mutableListOf(), { wishlist.contains(it) }, ::showProduct, ::quickAdd, ::toggleWish, { toggles.optBoolean("f_p_brand", true) }, { toggles.optBoolean("f_p_oldprice", true) }, ::buyNow, { toggles.optBoolean("f_s_wishlist", true) })
        b.rvFlash.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false); b.rvFlash.adapter = flashAdapter
        b.rvProducts.layoutManager = GridLayoutManager(this, 2); b.rvProducts.adapter = productAdapter
        b.rvCategories.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false); b.rvCategories.adapter = categoryAdapter
        b.swipeRefresh.setOnRefreshListener { loadStore() }
        b.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = applyFilter()
            override fun afterTextChanged(s: android.text.Editable?) {
                pendingSearchSave?.let { searchHandler.removeCallbacks(it) }
                val term = s?.toString()?.trim().orEmpty()
                if (term.length >= 2) {
                    pendingSearchSave = Runnable { saveRecentSearch(term) }
                    searchHandler.postDelayed(pendingSearchSave!!, 1200L)
                }
                b.tvSearchClear.visibility = if (term.isBlank()) View.GONE else View.VISIBLE
                if (term.isBlank()) renderSearchHistory()
            }
        })
        b.etSearch.setOnFocusChangeListener { _, focused -> if (focused && b.etSearch.text.isNullOrBlank()) renderSearchHistory() }
        b.tvSearchClear.setOnClickListener { b.etSearch.text?.clear(); b.etSearch.requestFocus(); renderSearchHistory() }
        b.tvVoiceSearch.setOnClickListener { startVoiceSearch() }
        b.btnScrollTop.setOnClickListener { b.mainScroll.smoothScrollTo(0, 0) }
        b.mainScroll.setOnScrollChangeListener { _, _, y, _, _ -> b.btnScrollTop.visibility = if (y > 320) View.VISIBLE else View.GONE }
        renderSearchHistory()
        b.tvAccount.setOnClickListener { showProfile() }
        b.tvStoreName.setOnClickListener { categoryFilter = "all"; b.tvProductHeading.text = "সকল পণ্য"; applyFilter() }
        b.tvHeaderChat.setOnClickListener { openChat() }
        b.tvHeaderWhatsApp.setOnClickListener { openStoreWhatsApp() }
        b.tvHeaderCart.setOnClickListener { showCart() }
        b.tvTheme.setOnClickListener { val dark = !prefs.getBoolean("dark_mode", false); prefs.edit().putBoolean("dark_mode", dark).apply(); recreate() }
        b.navHome.setOnClickListener { categoryFilter = "all"; b.tvProductHeading.text = "সকল পণ্য"; applyFilter() }
        b.btnFilter.setOnClickListener { showFilters() }
        b.btnLoadMore.setOnClickListener { showNextProductPage() }
        b.tvProductHeading.setOnClickListener { showCategoryPage() }
        b.navCatalog.setOnClickListener { showCategoryPage() }
        b.navWishlist.setOnClickListener { if(toggles.optBoolean("f_s_wishlist",true)) showWishlist() else toast("পছন্দের তালিকা Admin থেকে বন্ধ রাখা হয়েছে") }
        b.navCart.setOnClickListener { showCart() }
        b.navProfile.setOnClickListener { showProfile() }
        updateHeaderCartCount()
        loadStore()
    }

    private fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "পণ্যের নাম বলুন")
        }
        runCatching { startActivityForResult(intent, VOICE_SEARCH_REQUEST) }
            .onFailure { toast("এই ফোনে ভয়েস সার্চ চালু করা যাচ্ছে না") }
    }

    @Deprecated("Deprecated by Android, retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VOICE_SEARCH_REQUEST && resultCode == RESULT_OK) {
            val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) {
                b.etSearch.setText(spoken)
                b.etSearch.setSelection(spoken.length)
            } else toast("কথা বোঝা যায়নি")
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); initialDeepLinkHandled=true; handleDeepLink(intent) }
    override fun onResume() { super.onResume(); startRealtime(); refreshHandler.removeCallbacks(refreshFallback); refreshHandler.postDelayed(refreshFallback, 60_000L); flashHandler.removeCallbacks(flashTicker); flashHandler.post(flashTicker) }
    override fun onPause() { refreshHandler.removeCallbacks(refreshFallback); bannerSlideHandler.removeCallbacks(bannerSlideRunnable); flashHandler.removeCallbacks(flashTicker); activeRealtime?.close(); activeRealtime = null; super.onPause() }

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
        updateHeaderCartCount()
    }

    private fun updateHeaderCartCount() {
        if (!::b.isInitialized) return
        val count = cart.values.sumOf { it.qty }
        b.tvHeaderCart.text = "🛒 $count"
        b.navWishlist.text = "♡\nপছন্দ (${wishlist.size})"
    }

    private fun openStoreWhatsApp() {
        val number = settings.optString("whatsapp").ifBlank { settings.optString("phone") }.filter { it.isDigit() }
        if (number.isBlank()) { toast("WhatsApp নম্বর সেট করা নেই"); return }
        val uri = Uri.parse("https://wa.me/88${number.removePrefix("88")}")
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }.onFailure { toast("WhatsApp খোলা যায়নি") }
    }

    private fun loadStore() { lifecycleScope.launch {
        b.swipeRefresh.isRefreshing = true
        try {
            val s = api.select("settings", "select=*")
            for (i in 0 until s.length()) { val row=s.getJSONObject(i); when(row.optString("id")) { "global_config" -> settings=row.optJSONObject("data") ?: JSONObject(); "feature_toggles" -> toggles=row.optJSONObject("data") ?: JSONObject() } }
            applyAdminVisibility()
            val c=api.select("categories", "select=*&order=sort_order.asc"); categories.clear(); for(i in 0 until c.length()) if(c.getJSONObject(i).optBoolean("is_active",true)) categories.add(c.getJSONObject(i)); categoryAdapter.replace(categories)
            val ba=api.select("banners", "select=*&order=sort_order.asc"); banners.clear(); for(i in 0 until ba.length()) if(ba.getJSONObject(i).optBoolean("is_active",true)) banners.add(ba.getJSONObject(i))
            val p=api.select("products", "select=*&order=created_at.desc&limit=600"); products.clear(); for(i in 0 until p.length()) { val x=p.getJSONObject(i); if(x.optBoolean("is_active",true) && (!toggles.optBoolean("f_s_hide_out_stock",false) || availableStock(x, "", "")>0)) products.add(x) }
            val flash = products.filter { p -> p.optBoolean("is_flash_sale", false) && (p.optString("flash_ends_at").isBlank() || p.optString("flash_ends_at").take(19) > SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.US).format(Date())) }
            b.flashSection.visibility = if (flash.isNotEmpty() && toggles.optBoolean("f_s_flash_sale", true)) View.VISIBLE else View.GONE
            flashAdapter.replace(flash)
            updateFlashCountdown()
            b.tvNotice.visibility=if(toggles.optBoolean("f_s_marquee_notice",true)) View.VISIBLE else View.GONE
            b.tvNotice.text=if(settings.optBoolean("maintenance",false)) "দোকানটি সাময়িক রক্ষণাবেক্ষণে আছে" else settings.optString("marquee", "MM BAZAAR — আপনার বিশ্বস্ত অনলাইন শপ")
            b.tvNotice.isSingleLine = true
            b.tvNotice.ellipsize = TextUtils.TruncateAt.MARQUEE
            b.tvNotice.marqueeRepeatLimit = -1
            b.tvNotice.isSelected = true
            renderOffers()
            renderRecentlyViewed()
            b.tvStoreName.text = settings.optString("store_name", "MM BAZAAR").ifBlank { "MM BAZAAR" }
            b.tvStoreTagline.text = settings.optString("tagline").ifBlank { settings.optString("store_tagline").ifBlank { "আপনার বিশ্বস্ত শপ" } }
            val bannersOn=toggles.optBoolean("f_s_banners",true) && banners.isNotEmpty()
            b.ivBanner.visibility=if(bannersOn) View.VISIBLE else View.GONE
            b.tvBanner.visibility=if(bannersOn) View.VISIBLE else View.GONE
            b.bannerDots.visibility=if(bannersOn && banners.size > 1) View.VISIBLE else View.GONE
            b.tvBanner.text=banners.firstOrNull()?.optString("title")?.ifBlank { settings.optString("store_name","MM BAZAAR") } ?: settings.optString("store_name","MM BAZAAR")
            if(bannersOn) {
                bannerIndex = 0
                showCurrentBanner()
                bannerSlideHandler.removeCallbacks(bannerSlideRunnable)
                bannerSlideHandler.postDelayed(bannerSlideRunnable, 4500L)
                b.ivBanner.isClickable = false
                b.tvBanner.isClickable = false
                var bannerDownX = 0f
                b.bannerFrame.setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> { bannerDownX = event.x; true }
                        MotionEvent.ACTION_UP -> {
                            val delta = event.x - bannerDownX
                            if (kotlin.math.abs(delta) > 45 && banners.size > 1) {
                                bannerIndex = (bannerIndex + (if (delta < 0) 1 else -1) + banners.size) % banners.size
                                showCurrentBanner()
                            } else {
                                val url = banners.getOrNull(bannerIndex)?.optString("link_url").orEmpty()
                                if (url.startsWith("http")) runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                    .onFailure { toast("ব্যানারের লিংক খোলা যায়নি") }
                            }
                            true
                        }
                        else -> true
                    }
                }
            } else {
                bannerSlideHandler.removeCallbacks(bannerSlideRunnable)
                b.bannerFrame.setOnTouchListener(null)
            }
            if(api.userId!=null) { refreshUserData(); refreshChatBadge() } else { b.tvHeaderChat.text="💬" }
            applyFilter()
            if (!initialDeepLinkHandled) { initialDeepLinkHandled = true; handleDeepLink(intent) }
        } catch(e:Exception) { toast("ডেটা লোড হয়নি: ${e.message}") } finally { b.swipeRefresh.isRefreshing=false }
    } }

    private fun applyAdminVisibility() {
        b.tvHeaderWhatsApp.visibility = if (toggles.optBoolean("f_s_wa_checkout", true)) View.VISIBLE else View.GONE
        b.navWishlist.visibility = if (toggles.optBoolean("f_s_wishlist", true)) View.VISIBLE else View.GONE
        if (settings.optBoolean("maintenance", false)) {
            if (maintenanceDialog?.isShowing != true && !isFinishing) {
                maintenanceDialog = AlertDialog.Builder(this)
                    .setTitle("দোকান সাময়িক বন্ধ")
                    .setMessage("দোকানটি বর্তমানে রক্ষণাবেক্ষণে আছে। Admin চালু না করা পর্যন্ত অ্যাপ ব্যবহার করা যাবে না।")
                    .setCancelable(false)
                    .create().also { dialog -> dialog.setOnDismissListener { maintenanceDialog = null }; dialog.show() }
            }
        } else {
            maintenanceDialog?.dismiss()
            maintenanceDialog = null
        }
    }

    private fun startRealtime() {
        activeRealtime?.close()
        activeRealtime = StoreRealtime(api) { table ->
            runOnUiThread {
                when (table) {
                    "products", "categories", "banners", "coupons", "settings" -> loadStore()
                    "users" -> refreshUserData()
                    "chats", "chat_messages" -> {
                        refreshChatBadge()
                        val view = activeChatView
                        val uid = api.userId
                        if (view != null && uid != null) loadChat(uid, view)
                    }
                    else -> refreshChatBadge()
                }
            }
        }.also { it.connect() }
    }
    private fun refreshUserData() { lifecycleScope.launch { runCatching { val u=api.select("users","select=*&id=eq.${api.userId}"); if(u.length()>0) userData=u.getJSONObject(0); refreshChatBadge() } } }

    private fun renderOffers() {
        val target = b.offerContainer; target.removeAllViews()
        val defaults = listOf(Triple("offer1_icon", "offer1_title", "offer1_sub"), Triple("offer2_icon", "offer2_title", "offer2_sub"), Triple("offer3_icon", "offer3_title", "offer3_sub"))
        defaults.forEachIndexed { i, keys ->
            val defaultsText = listOf(Triple("🚚", "ফ্রি ডেলিভারি", "৳১০০০+ অর্ডারে"), Triple("🎁", "১০% ছাড়", "প্রথম অর্ডারে"), Triple("⚡", "ফ্ল্যাশ সেল", "বিশেষ অফার"))[i]
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 10, 14, 10); background = getDrawable(R.drawable.bg_card); layoutParams = LinearLayout.LayoutParams((resources.displayMetrics.widthPixels * .40f).toInt(), -2).apply { setMargins(6, 4, 6, 4) } }
            card.addView(TextView(this).apply { text = settings.optString(keys.first, defaultsText.first); textSize = 20f })
            card.addView(TextView(this).apply { text = settings.optString(keys.second, defaultsText.second); textSize = 13f; setTextColor(getColor(R.color.text_main)) })
            card.addView(TextView(this).apply { text = settings.optString(keys.third, defaultsText.third); textSize = 11f; setTextColor(getColor(R.color.text_secondary)) })
            target.addView(card)
        }
    }

    private fun renderRecentlyViewed() {
        val target = b.recentContainer; target.removeAllViews()
        val ids = runCatching { val a = JSONArray(prefs.getString("recent_products", "[]")); (0 until a.length()).map { a.optString(it) } }.getOrDefault(emptyList())
        val recent = ids.mapNotNull { id -> products.firstOrNull { it.optString("id") == id } }.take(6)
        recent.forEach { p ->
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = getDrawable(R.drawable.bg_card); setPadding(8,8,8,8); layoutParams = LinearLayout.LayoutParams((resources.displayMetrics.widthPixels * .36f).toInt(), -2).apply { setMargins(6,4,6,8) }; setOnClickListener { showProduct(p) } }
            val image = ImageView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 104); scaleType = ImageView.ScaleType.CENTER_CROP; load(p.optString("image_url").ifBlank { null }) { placeholder(R.drawable.bg_card); error(R.drawable.bg_card) } }
            card.addView(image)
            card.addView(TextView(this).apply { text = p.optString("title"); maxLines = 2; textSize = 12f; setTextColor(getColor(R.color.text_main)); setPadding(2,6,2,2) })
            card.addView(TextView(this).apply { text = "৳${money(p.optDouble("price"))}"; textSize = 13f; setTextColor(getColor(R.color.gold_dark)); setPadding(2,2,2,2) })
            target.addView(card)
        }
        b.recentSection.visibility = if (recent.isEmpty()) View.GONE else View.VISIBLE
    }


    private fun saveRecentSearch(term: String) {
        val old = runCatching { JSONArray(prefs.getString("recent_searches", "[]")) }.getOrElse { JSONArray() }
        val items = (0 until old.length()).map { old.optString(it) }.filter { !it.equals(term, true) }.toMutableList()
        items.add(0, term); val saved = JSONArray(); items.take(6).forEach { saved.put(it) }; prefs.edit().putString("recent_searches", saved.toString()).apply(); renderSearchHistory()
    }
    private fun renderSearchHistory() {
        val target=b.searchHistoryContainer; target.removeAllViews()
        val a=runCatching { JSONArray(prefs.getString("recent_searches", "[]")) }.getOrElse { JSONArray() }
        if(a.length()==0 || !b.etSearch.hasFocus() || !b.etSearch.text.isNullOrBlank()){ target.parent?.let { (it as? View)?.visibility=View.GONE }; return }
        for(i in 0 until a.length()) { val term=a.optString(i); val chip=TextView(this).apply { text=term; setPadding(18,9,18,9); setTextColor(getColor(R.color.text_main)); background=getDrawable(R.drawable.bg_card); layoutParams=LinearLayout.LayoutParams(-2,-2).apply{setMargins(4,0,4,0)}; setOnClickListener { b.etSearch.setText(term); b.etSearch.setSelection(term.length); b.etSearch.clearFocus(); target.parent?.let{(it as? View)?.visibility=View.GONE} } }; target.addView(chip) }
        val clear=TextView(this).apply { text="সব মুছুন"; setPadding(14,9,14,9); setTextColor(getColor(R.color.danger)); setOnClickListener { prefs.edit().remove("recent_searches").apply(); renderSearchHistory() } }; target.addView(clear)
        target.parent?.let { (it as? View)?.visibility=View.VISIBLE }
    }
    private fun updateFlashCountdown() {
        val now=System.currentTimeMillis()
        val next=products.filter{it.optBoolean("is_flash_sale",false)}.mapNotNull{p->p.optString("flash_ends_at").takeIf{it.isNotBlank()}?.let{ raw -> parseIsoMillis(raw)?.let{it to p} }}.filter{it.first>now}.minByOrNull{it.first}
        if(next==null){ b.tvFlashCountdown.text="বিশেষ ফ্ল্যাশ অফার"; return }
        val millis=next.first-now
        if(millis<=0){ b.tvFlashCountdown.text="ফ্ল্যাশ সেলের সময় শেষ"; return }
        val sec=millis/1000; val days=sec/86400; val hours=(sec%86400)/3600; val mins=(sec%3600)/60; val seconds=sec%60
        b.tvFlashCountdown.text="সময় বাকি: ${if(days>0)"${days} দিন " else ""}${String.format(Locale.US,"%02d:%02d:%02d",hours,mins,seconds)}"
    }
    private fun parseIsoMillis(raw:String):Long? {
        val formats=listOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX","yyyy-MM-dd'T'HH:mm:ssXXX","yyyy-MM-dd'T'HH:mm:ss.SSS'Z'","yyyy-MM-dd'T'HH:mm:ss'Z'")
        for(pattern in formats){ val parsed=runCatching{SimpleDateFormat(pattern,Locale.US).apply{timeZone=java.util.TimeZone.getTimeZone("UTC")}.parse(raw)?.time}.getOrNull(); if(parsed!=null)return parsed }
        return null
    }
    private fun showImageZoom(url:String, gallery:List<String>) {
        if(url.isBlank()) return
        val image=ImageView(this).apply { layoutParams=LinearLayout.LayoutParams(-1,(resources.displayMetrics.heightPixels*.62f).toInt()); scaleType=ImageView.ScaleType.FIT_CENTER; load(url) }
        val dialog=AlertDialog.Builder(this).setView(image).setPositiveButton("বন্ধ",null).create()
        var index=gallery.indexOf(url).coerceAtLeast(0)
        image.setOnTouchListener(object: View.OnTouchListener { var down=0f; override fun onTouch(v:View,e:MotionEvent):Boolean { when(e.action){MotionEvent.ACTION_DOWN->{down=e.x;return true};MotionEvent.ACTION_UP->{val dx=e.x-down;if(kotlin.math.abs(dx)>70 && gallery.isNotEmpty()){index=(index+(if(dx<0) 1 else -1)+gallery.size)%gallery.size;image.load(gallery[index]);return true}}};return true} })
        dialog.show()
    }
    private fun handleDeepLink(i:Intent?) {
        val uri=i?.data ?: return
        val page=(uri.getQueryParameter("go") ?: uri.getQueryParameter("page") ?: if(uri.host=="product")"product" else "").lowercase()
        val pid=uri.getQueryParameter("p") ?: uri.getQueryParameter("id") ?: uri.lastPathSegment?.takeIf{page=="product"}
        when(page){"home"->{categoryFilter="all";applyFilter()};"category","products"->showCategoryPage();"cart"->showCart();"wishlist"->showWishlist();"profile"->showProfile();"product"->if(!pid.isNullOrBlank())openProductById(pid)}
        if(!pid.isNullOrBlank() && page!="product") openProductById(pid)
    }
    private fun openProductById(id:String) { val p=products.firstOrNull{it.optString("id")==id}; if(p!=null) showProduct(p) else lifecycleScope.launch { runCatching { val a=api.select("products","select=*&id=eq.${Uri.encode(id)}&limit=1"); if(a.length()>0) showProduct(a.getJSONObject(0)) else toast("পণ্যটি খুঁজে পাওয়া যায়নি") }.onFailure{toast("পণ্য লোড হয়নি: ${it.message}")} } }

    private fun showCurrentBanner() {
        val item = banners.getOrNull(bannerIndex) ?: return
        item.optString("image_url").takeIf { it.isNotBlank() }?.let { b.ivBanner.load(it) { crossfade(true) } }
        b.tvBanner.text = item.optString("title").ifBlank { settings.optString("store_name", "MM BAZAAR") }
        b.bannerDots.removeAllViews()
        if (banners.size > 1) {
            banners.indices.forEach { index ->
                val dot = TextView(this).apply {
                    text = if (index == bannerIndex) "●" else "○"
                    textSize = 11f
                    setTextColor(android.graphics.Color.WHITE)
                    setPadding(5, 0, 5, 0)
                    setOnClickListener { bannerIndex = index; showCurrentBanner() }
                }
                b.bannerDots.addView(dot)
            }
        }
    }

    private fun smartSearchTokens(raw: String): List<String> {
        val normalized = raw.lowercase(Locale.ROOT).trim().normalizeNfc()
        if (normalized.isBlank()) return emptyList()
        val tokens = linkedSetOf(normalized)
        smartSearchSynonyms.forEach { group ->
            val normalizedGroup = group.map { it.normalizeNfc() }
            if (normalizedGroup.any { it.contains(normalized) || (normalized.length >= 2 && normalized.contains(it)) }) tokens.addAll(normalizedGroup)
        }
        return tokens.toList()
    }

    private fun String.normalizeNfc(): String = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFC)

    private fun applyFilter(resetPage: Boolean = true) {
        val q=b.etSearch.text?.toString()?.trim().orEmpty()
        val tokens=smartSearchTokens(q)
        val list=products.filter { p ->
            val cat=categories.firstOrNull { it.optString("id")==p.optString("category_id") }?.optString("name").orEmpty()
            val price=p.optDouble("price")
            val haystack=listOf(p.optString("title"),p.optString("brand"),p.optString("description"),cat,p.optString("price")).joinToString(" ").lowercase(Locale.ROOT).normalizeNfc()
            (categoryFilter=="all" || p.optString("category_id")==categoryFilter) &&
                (tokens.isEmpty() || tokens.any { haystack.contains(it) }) &&
                price>=minPrice && price<=maxPrice && (!onlyInStock || availableStock(p,"","")>0)
        }.toMutableList()
        when(sortMode){"price_asc"->list.sortBy{it.optDouble("price")};"price_desc"->list.sortByDescending{it.optDouble("price")};else->list.sortByDescending{it.optString("created_at")}}
        filteredProducts = list
        if (resetPage) productDisplayLimit = productPageSize
        productAdapter.replace(filteredProducts.take(productDisplayLimit))
        b.tvProductCount.text="${list.size} টি পণ্য"
        b.tvEmpty.text = "কোনো পণ্য পাওয়া যায়নি"
        b.tvEmpty.visibility=if(list.isEmpty()) View.VISIBLE else View.GONE
        b.btnLoadMore.visibility=if(filteredProducts.size>productDisplayLimit) View.VISIBLE else View.GONE
        b.btnLoadMore.text="আরও পণ্য দেখুন (${(filteredProducts.size-productDisplayLimit).coerceAtLeast(0)} বাকি)"
    }

    private fun showNextProductPage() {
        if (productDisplayLimit >= filteredProducts.size) { b.btnLoadMore.visibility=View.GONE; return }
        productDisplayLimit = (productDisplayLimit + productPageSize).coerceAtMost(filteredProducts.size)
        productAdapter.replace(filteredProducts.take(productDisplayLimit))
        b.btnLoadMore.visibility=if(productDisplayLimit<filteredProducts.size) View.VISIBLE else View.GONE
        b.btnLoadMore.text="আরও পণ্য দেখুন (${(filteredProducts.size-productDisplayLimit).coerceAtLeast(0)} বাকি)"
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

    private fun showCategoryPage() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 12, 18, 8) }
        val categorySearch = EditText(this).apply { hint = "ক্যাটাগরির ভেতর সার্চ..."; setSingleLine(true); inputType = InputType.TYPE_CLASS_TEXT }
        box.addView(categorySearch)
        val selector = Spinner(this)
        selector.adapter = spinnerAdapter(listOf("সব ক্যাটাগরি") + categories.map { it.optString("name") })
        val currentIndex = if (categoryFilter == "all") 0 else categories.indexOfFirst { it.optString("id") == categoryFilter }.let { if (it < 0) 0 else it + 1 }
        selector.setSelection(currentIndex)
        box.addView(label("ক্যাটাগরি নির্বাচন করুন")); box.addView(selector)

        val minField = EditText(this).apply {
            hint = "সর্বনিম্ন দাম (৳)"; inputType = InputType.TYPE_CLASS_NUMBER
            setText(if (minPrice > 0) minPrice.toInt().toString() else "")
        }
        val maxField = EditText(this).apply {
            hint = "সর্বোচ্চ দাম (৳)"; inputType = InputType.TYPE_CLASS_NUMBER
            setText(if (maxPrice < Double.MAX_VALUE) maxPrice.toInt().toString() else "")
        }
        box.addView(label("দামের সীমা")); box.addView(minField); box.addView(maxField)
        val stockOnly = CheckBox(this).apply { text = "শুধু স্টকে থাকা পণ্য"; isChecked = onlyInStock }
        box.addView(stockOnly)
        val sort = Spinner(this).apply {
            adapter = spinnerAdapter(listOf("নতুন আগে", "দাম কম থেকে বেশি", "দাম বেশি থেকে কম"))
            setSelection(when (sortMode) { "price_asc" -> 1; "price_desc" -> 2; else -> 0 })
        }
        box.addView(label("পণ্য সাজান")); box.addView(sort)

        val count = TextView(this).apply { setPadding(0, 12, 0, 4); setTextColor(getColor(R.color.text_secondary)) }
        var previewRows: List<JSONObject> = emptyList()
        var previewLimit = productPageSize
        val previewMore = android.widget.Button(this).apply { text = "আরও পণ্য দেখুন"; visibility = View.GONE }
        val list = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            isNestedScrollingEnabled = false
        }
        val adapter = ProductAdapter(mutableListOf(), { wishlist.contains(it) }, ::showProduct, ::quickAdd, ::toggleWish,
            { toggles.optBoolean("f_p_brand", true) }, { toggles.optBoolean("f_p_oldprice", true) }, ::buyNow, { toggles.optBoolean("f_s_wishlist", true) })
        list.adapter = adapter

        fun refreshPreview() {
            val id = if (selector.selectedItemPosition == 0) "all" else categories.getOrNull(selector.selectedItemPosition - 1)?.optString("id").orEmpty()
            val minValue = minField.text.toString().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
            val maxValue = maxField.text.toString().toDoubleOrNull()?.takeIf { it > 0 } ?: Double.MAX_VALUE
            val query = b.etSearch.text?.toString()?.trim()?.lowercase().orEmpty()
            val localQuery = categorySearch.text?.toString()?.trim()?.lowercase().orEmpty()
            val rows = products.filter { product ->
                val categoryName = categories.firstOrNull { it.optString("id") == product.optString("category_id") }?.optString("name").orEmpty()
                val searchable = listOf(product.optString("title"), product.optString("brand"), product.optString("description"), categoryName)
                val matchesQuery = (query.isBlank() || searchable.any { it.lowercase().contains(query) }) && (localQuery.isBlank() || searchable.any { it.lowercase().contains(localQuery) })
                (id == "all" || product.optString("category_id") == id) && matchesQuery &&
                    product.optDouble("price") >= minValue && product.optDouble("price") <= maxValue &&
                    (!stockOnly.isChecked || availableStock(product, "", "") > 0)
            }.toMutableList()
            when (sort.selectedItemPosition) {
                1 -> rows.sortBy { it.optDouble("price") }
                2 -> rows.sortByDescending { it.optDouble("price") }
                else -> rows.sortByDescending { it.optString("created_at") }
            }
            previewRows = rows
            previewLimit = productPageSize
            adapter.replace(previewRows.take(previewLimit))
            count.text = "${rows.size} টি পণ্য পাওয়া গেছে"
            previewMore.visibility = if (previewRows.size > previewLimit) View.VISIBLE else View.GONE
            previewMore.text = "আরও পণ্য দেখুন (${(previewRows.size-previewLimit).coerceAtLeast(0)} বাকি)"
        }
        previewMore.setOnClickListener {
            previewLimit = (previewLimit + productPageSize).coerceAtMost(previewRows.size)
            adapter.replace(previewRows.take(previewLimit))
            previewMore.visibility = if (previewLimit < previewRows.size) View.VISIBLE else View.GONE
            previewMore.text = "আরও পণ্য দেখুন (${(previewRows.size-previewLimit).coerceAtLeast(0)} বাকি)"
        }
        categorySearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refreshPreview() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        selector.onItemSelectedListener = spinnerListener { refreshPreview() }
        sort.onItemSelectedListener = spinnerListener { refreshPreview() }
        stockOnly.setOnCheckedChangeListener { _, _ -> refreshPreview() }
        box.addView(count)
        box.addView(list, LinearLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * .34f).toInt()))
        box.addView(previewMore, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT))
        refreshPreview()

        val dialog = AlertDialog.Builder(this).setTitle("ক্যাটালগ / ক্যাটাগরি")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("ফিল্টার প্রয়োগ করুন", null)
            .setNeutralButton("রিসেট", null)
            .setNegativeButton("বন্ধ", null).create()
        dialog.setOnShowListener {
            dialog.window?.setLayout(-1, -1)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                categoryFilter = if (selector.selectedItemPosition == 0) "all" else categories.getOrNull(selector.selectedItemPosition - 1)?.optString("id") ?: "all"
                minPrice = minField.text.toString().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                maxPrice = maxField.text.toString().toDoubleOrNull()?.takeIf { it > 0 } ?: Double.MAX_VALUE
                onlyInStock = stockOnly.isChecked
                sortMode = when (sort.selectedItemPosition) { 1 -> "price_asc"; 2 -> "price_desc"; else -> "newest" }
                b.tvProductHeading.text = if (categoryFilter == "all") "সকল পণ্য" else categories.firstOrNull { it.optString("id") == categoryFilter }?.optString("name") ?: "পণ্য"
                applyFilter(); dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                selector.setSelection(0); minField.text.clear(); maxField.text.clear(); stockOnly.isChecked = false; sort.setSelection(0)
                minPrice = 0.0; maxPrice = Double.MAX_VALUE; onlyInStock = false; sortMode = "newest"
                categoryFilter = "all"; refreshPreview(); b.tvProductHeading.text = "সকল পণ্য"; applyFilter()
            }
        }
        dialog.show()
    }
    private fun hasVariantStock(p:JSONObject)=p.optJSONObject("variant_stock")!=null
    private fun availableStock(p:JSONObject,size:String,color:String):Int {
        val total=p.optInt("stock",0).coerceAtLeast(0); val vs=p.optJSONObject("variant_stock") ?: return total
        listOf("$size|$color","$size|","|$color","|").forEach { key -> if(vs.has(key)) return vs.optInt(key,total).coerceAtLeast(0) }
        return total
    }
    private fun addLine(p:JSONObject,size:String,color:String,qty:Int=1) {
        if (settings.optBoolean("maintenance", false)) { toast("দোকানটি সাময়িক রক্ষণাবেক্ষণে আছে"); return }
        val left=availableStock(p,size,color); val key=cartKey(p.optString("id"),size,color); val old=cart[key]?.qty ?: 0
        if(left<=old) { toast("এই ভ্যারিয়েন্টের পর্যাপ্ত স্টক নেই"); return }
        val line=cart[key]; if(line==null) cart[key]=CartLine(p,qty,size,color) else line.qty=(line.qty+qty).coerceAtMost(left)
        saveLocal(); toast("কার্টে যোগ হয়েছে")
    }
    private fun quickAdd(p:JSONObject) {
        val size = if (toggles.optBoolean("f_p_size", true)) stringArray(p.optJSONArray("sizes")).firstOrNull().orEmpty() else ""
        val color = if (toggles.optBoolean("f_p_color", true)) stringArray(p.optJSONArray("colors")).firstOrNull().orEmpty() else ""
        addLine(p, size, color)
    }
    private fun buyNow(p: JSONObject) {
        if (settings.optBoolean("maintenance", false)) { toast("দোকানটি সাময়িক রক্ষণাবেক্ষণে আছে"); return }
        val size = if (toggles.optBoolean("f_p_size", true)) stringArray(p.optJSONArray("sizes")).firstOrNull().orEmpty() else ""
        val color = if (toggles.optBoolean("f_p_color", true)) stringArray(p.optJSONArray("colors")).firstOrNull().orEmpty() else ""
        val key = cartKey(p.optString("id"), size, color)
        val before = cart[key]?.qty ?: 0
        addLine(p, size, color)
        if ((cart[key]?.qty ?: 0) > before) showCheckout()
    }
    private fun toggleWish(p:JSONObject) { if(!toggles.optBoolean("f_s_wishlist",true)){toast("পছন্দের তালিকা Admin থেকে বন্ধ রাখা হয়েছে");return}; val id=p.optString("id"); if(!wishlist.add(id)) wishlist.remove(id); saveLocal(); toast(if(wishlist.contains(id)) "পছন্দের তালিকায় যোগ হয়েছে" else "পছন্দের তালিকা থেকে সরানো হয়েছে") }
    private fun showWishlist() {
        if (!toggles.optBoolean("f_s_wishlist", true)) { toast("পছন্দের তালিকা Admin থেকে বন্ধ রাখা হয়েছে"); return }
        val rows = products.filter { wishlist.contains(it.optString("id")) }
        filteredProducts = rows
        productDisplayLimit = rows.size
        productAdapter.replace(rows)
        b.btnLoadMore.visibility = View.GONE
        b.tvProductHeading.text = "পছন্দের তালিকা"
        b.tvProductCount.text = "${wishlist.size} টি পণ্য"
        b.tvEmpty.text = "আপনার পছন্দের তালিকা খালি"
        b.tvEmpty.visibility = if (wishlist.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showProduct(p:JSONObject) {
        val recent = runCatching { JSONArray(prefs.getString("recent_products", "[]")) }.getOrElse { JSONArray() }
        val ids = (0 until recent.length()).map { recent.optString(it) }.filter { it != p.optString("id") }.toMutableList(); ids.add(0, p.optString("id")); val saved = JSONArray(); ids.take(10).forEach { saved.put(it) }; prefs.edit().putString("recent_products", saved.toString()).apply(); renderRecentlyViewed()
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(22,12,22,8) }
        val galleryUrls = (listOf(p.optString("image_url")) + stringArray(p.optJSONArray("image_gallery")) + stringArray(p.optJSONArray("imageGallery"))).filter { it.isNotBlank() }.distinct()
        var selectedImage = galleryUrls.firstOrNull().orEmpty()
        val image=ImageView(this).apply { layoutParams=LinearLayout.LayoutParams(-1,210); scaleType=ImageView.ScaleType.FIT_CENTER; load(selectedImage); setOnClickListener { showImageZoom(selectedImage, galleryUrls) } }
        val galleryStrip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        galleryUrls.forEach { url -> val thumb = ImageView(this).apply { layoutParams = LinearLayout.LayoutParams(66,66).apply { setMargins(4,8,4,8) }; scaleType = ImageView.ScaleType.CENTER_CROP; load(url); setOnClickListener { selectedImage = url; image.load(url); } }; galleryStrip.addView(thumb) }
        val title=TextView(this).apply { text=p.optString("title"); textSize=19f; setTextColor(getColor(R.color.text_main)); setPadding(0,10,0,4) }
        val price=TextView(this).apply { text="৳ ${money(p.optDouble("price"))}"; textSize=20f; setTextColor(getColor(R.color.gold_dark)) }
        val desc=TextView(this).apply { text=p.optString("description","বিবরণ নেই"); textSize=14f; setTextColor(getColor(R.color.text_secondary)); setPadding(0,8,0,8) }
        box.addView(image); if (galleryUrls.size > 1) box.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(galleryStrip) }); box.addView(title); box.addView(price)
        val share = Button(this).apply { text = "পণ্য শেয়ার করুন"; setOnClickListener { shareProduct(p) } }; box.addView(share)
        val sizes=stringArray(p.optJSONArray("sizes")); val colors=stringArray(p.optJSONArray("colors"))
        val sizeSpin=Spinner(this); val colorSpin=Spinner(this)
        if(sizes.isNotEmpty() && toggles.optBoolean("f_p_size",true)) { box.addView(label("সাইজ নির্বাচন করুন")); sizeSpin.adapter=spinnerAdapter(sizes); box.addView(sizeSpin) }
        if(colors.isNotEmpty() && toggles.optBoolean("f_p_color",true)) { box.addView(label("কালার নির্বাচন করুন")); colorSpin.adapter=spinnerAdapter(colors); box.addView(colorSpin) }
        val stock=TextView(this).apply { setPadding(0,8,0,8) }; box.addView(stock)
        fun refreshStock() { val sz=if(sizeSpin.parent!=null && sizes.isNotEmpty() && toggles.optBoolean("f_p_size",true)) sizeSpin.selectedItem?.toString().orEmpty() else ""; val co=if(colorSpin.parent!=null && colors.isNotEmpty() && toggles.optBoolean("f_p_color",true)) colorSpin.selectedItem?.toString().orEmpty() else ""; val n=availableStock(p,sz,co); stock.text=if(n>0) "এই ভ্যারিয়েন্টে স্টক: $n" else "এই ভ্যারিয়েন্টের স্টক শেষ"; stock.setTextColor(getColor(if(n>0) R.color.success else R.color.danger)) }
        sizeSpin.onItemSelectedListener=spinnerListener { refreshStock() }; colorSpin.onItemSelectedListener=spinnerListener { refreshStock() }; refreshStock(); box.addView(desc)
        val reviews=TextView(this).apply { text="রিভিউ লোড হচ্ছে…"; setPadding(0,8,0,4); textSize=13f }; box.addView(reviews)
        lifecycleScope.launch { runCatching { val r=api.select("reviews","select=*&product_id=eq.${p.optString("id")}&order=created_at.desc&limit=10"); reviews.text=if(r.length()==0) "এখনো কোনো রিভিউ নেই" else (0 until r.length()).joinToString("\n\n") { i -> val x=r.getJSONObject(i); "${x.optString("user_name","গ্রাহক")}  ${"★".repeat(x.optInt("rating").coerceIn(0,5))}\n${x.optString("text")}\n${x.optString("reply").takeIf{it.isNotBlank()}?.let{"দোকানের উত্তর: $it"}.orEmpty()}" } } }
        val buy = Button(this).apply { text = "এখনই কিনুন" }; box.addView(buy)
        val d=AlertDialog.Builder(this).setTitle("পণ্যের বিবরণ").setView(ScrollView(this).apply{addView(box)}).setPositiveButton("কার্টে যোগ করুন",null).setNeutralButton("রিভিউ লিখুন",null).setNegativeButton("বন্ধ",null).create()
        buy.setOnClickListener { val sz=if(sizes.isNotEmpty() && toggles.optBoolean("f_p_size",true)) sizeSpin.selectedItem?.toString().orEmpty() else ""; val co=if(colors.isNotEmpty() && toggles.optBoolean("f_p_color",true)) colorSpin.selectedItem?.toString().orEmpty() else ""; if(availableStock(p,sz,co)>0){ addLine(p,sz,co); d.dismiss(); showCheckout() } else toast("স্টক নেই") }
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { val sz=if(sizes.isNotEmpty() && toggles.optBoolean("f_p_size",true)) sizeSpin.selectedItem?.toString().orEmpty() else ""; val co=if(colors.isNotEmpty() && toggles.optBoolean("f_p_color",true)) colorSpin.selectedItem?.toString().orEmpty() else ""; addLine(p,sz,co); if(availableStock(p,sz,co)>0) d.dismiss() }; d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { d.dismiss(); openReview(p) } }
        d.show()
    }

    private fun shareProduct(p: JSONObject) {
        val text = "${p.optString("title")}\nমূল্য: ৳${money(p.optDouble("price"))}\n${p.optString("description")}\nhttps://mm-bazaar.web.app/?p=${Uri.encode(p.optString("id"))}"
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "পণ্য শেয়ার করুন"))
    }

    private fun showCart() {
        if (cart.isEmpty()) { AlertDialog.Builder(this).setMessage("আপনার কার্ট খালি।").setPositiveButton("শপিং করুন") { _, _ -> b.mainScroll.smoothScrollTo(0,0) }.setNegativeButton("বন্ধ",null).show(); return }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16,8,16,8) }
        val rowsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var checkoutButton: Button? = null
        fun updateCheckoutAvailability() {
            val minimum = settings.optDouble("min_order", 0.0)
            checkoutButton?.apply {
                isEnabled = cart.isEmpty() || subtotal() >= minimum
                text = when {
                    cart.isEmpty() -> "শপিং করুন"
                    isEnabled -> "চেকআউট"
                    else -> "ন্যূনতম ৳${money(minimum)} অর্ডার করুন"
                }
            }
        }
        fun redraw() {
            rowsContainer.removeAllViews()
            if (cart.isEmpty()) {
                rowsContainer.addView(TextView(this).apply {
                    text = "আপনার কার্ট খালি। পছন্দের পণ্য খুঁজে শপিং শুরু করুন।"
                    gravity = Gravity.CENTER
                    setPadding(18, 28, 18, 28)
                    setTextColor(getColor(R.color.text_secondary))
                })
            }
            cart.values.toList().forEach { line ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(8,8,8,8); background = getDrawable(R.drawable.bg_card) }
                val image = ImageView(this).apply { layoutParams = LinearLayout.LayoutParams(64,64); scaleType = ImageView.ScaleType.CENTER_CROP; load(line.product.optString("image_url")) }
                val info = TextView(this).apply { text = "${line.product.optString("title")}\n${if(line.size.isNotBlank()) "সাইজ: ${line.size}  " else ""}${if(line.color.isNotBlank()) "কালার: ${line.color}" else ""}\n৳${money(line.product.optDouble("price")*line.qty)}"; layoutParams = LinearLayout.LayoutParams(0,-2,1f); setPadding(8,0,4,0); setTextColor(getColor(R.color.text_main)); textSize = 12f }
                val controls = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER }
                val plus = Button(this).apply { text="+"; minWidth=38; setOnClickListener { if(line.qty<availableStock(line.product,line.size,line.color)){line.qty++;saveLocal();redraw()}else toast("স্টক সীমা অতিক্রম করা যাবে না") } }
                val count = TextView(this).apply { text=line.qty.toString(); gravity=Gravity.CENTER; setTextColor(getColor(R.color.text_main)); setPadding(4,2,4,2) }
                val minus = Button(this).apply { text="−"; minWidth=38; setOnClickListener { line.qty--; if(line.qty<=0)cart.remove(cartKey(line.product.optString("id"),line.size,line.color));saveLocal();redraw() } }
                controls.addView(plus); controls.addView(count); controls.addView(minus)
                val del = TextView(this).apply { text = "✕"; setTextColor(getColor(R.color.danger)); setPadding(10,12,8,12); setOnClickListener { cart.remove(cartKey(line.product.optString("id"),line.size,line.color)); saveLocal(); redraw() } }
                row.addView(image); row.addView(info); row.addView(controls); row.addView(del)
                var downX = 0f; var downY = 0f
                info.setOnTouchListener { _, event -> when(event.action) {
                    MotionEvent.ACTION_DOWN -> { downX=event.rawX; downY=event.rawY; true }
                    MotionEvent.ACTION_UP -> {
                        val dx=event.rawX-downX; val dy=event.rawY-downY
                        if(dx < -85 && kotlin.math.abs(dx)>kotlin.math.abs(dy)) {
                            val key = cartKey(line.product.optString("id"),line.size,line.color)
                            val removed = cart.remove(key)
                            saveLocal(); redraw()
                            Snackbar.make(box, "পণ্যটি সরানো হয়েছে", Snackbar.LENGTH_LONG)
                                .setAction("আনডু") { if (removed != null) { cart[key] = removed; saveLocal(); redraw() } }
                                .show()
                        }
                        true
                    }
                    else -> true
                } }
                rowsContainer.addView(row); rowsContainer.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(-1,8) })
            }
            updateCheckoutAvailability()
        }
        box.addView(rowsContainer)
        val couponInput = EditText(this).apply { hint = "কুপন কোড"; setSingleLine(true); inputType = InputType.TYPE_CLASS_TEXT; setText(appliedCoupon) }
        val couponStatus = TextView(this).apply { setPadding(0,4,0,8); setTextColor(getColor(R.color.success)) }
        val discountBadge = TextView(this).apply { text="কুপন ছাড়"; setPadding(12,7,12,7); setTextColor(getColor(R.color.success)); background=getDrawable(R.drawable.bg_card); visibility=View.GONE }
        val couponRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val apply = Button(this).apply { text = if(appliedCoupon.isBlank()) "কুপন প্রয়োগ" else "কুপন আপডেট" }
        val remove = Button(this).apply { text = "বাদ দিন"; visibility = if(appliedCoupon.isBlank()) View.GONE else View.VISIBLE }
        couponRow.addView(couponInput, LinearLayout.LayoutParams(0,-2,1f)); couponRow.addView(apply); couponRow.addView(remove)
        box.addView(label("কুপন / ডিসকাউন্ট")); box.addView(couponRow); box.addView(couponStatus); box.addView(discountBadge)
        val zone = Spinner(this).apply { adapter = spinnerAdapter(listOf("ঢাকার ভিতরে", "ঢাকার বাইরে")); setSelection(if(currentZone=="in")0 else 1) }
        box.addView(label("ডেলিভারি এলাকা")); box.addView(zone)
        val summary = TextView(this).apply { setPadding(0,10,0,8); setTextColor(getColor(R.color.text_main)); textSize=13f }
        val progress = ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { max=100 }
        val progressText = TextView(this).apply { setPadding(0,4,0,8); setTextColor(getColor(R.color.gold_dark)); textSize=12f }
        box.addView(summary); box.addView(progress); box.addView(progressText)
        fun updateSummary() {
            currentZone = if(zone.selectedItemPosition==0) "in" else "out"
            val sub = subtotal(); val discount = discountPreview.coerceIn(0.0,sub); val after = (sub-discount).coerceAtLeast(0.0)
            val threshold = settings.optDouble("free_delivery_threshold",0.0); val free = threshold>0 && after>=threshold
            val delivery = if(free) 0.0 else settings.optDouble(if(currentZone=="in")"del_in" else "del_out",if(currentZone=="in")60.0 else 120.0)
            val tax = (after*settings.optDouble("tax",0.0)/100.0).roundToInt().toDouble()
            summary.text = "পণ্যমূল্য: ৳${money(sub)}\nছাড়: -৳${money(discount)}\nডেলিভারি: ${if(free)"ফ্রি" else "৳${money(delivery)}"}\nট্যাক্স: ৳${money(tax)}\nসর্বমোট: ৳${money(after+delivery+tax)}"
            if(threshold>0){ progress.visibility=View.VISIBLE; progress.progress=((after/threshold)*100).toInt().coerceIn(0,100); progressText.text=if(free)"ফ্রি ডেলিভারি পেয়েছেন" else "আর ৳${money(threshold-after)} যোগ করলে ফ্রি ডেলিভারি" } else { progress.visibility=View.GONE; progressText.text="" }
            couponStatus.text = if(appliedCoupon.isNotBlank()) "✓ $appliedCoupon কুপন প্রয়োগ হয়েছে" else ""
            discountBadge.text = "ডিসকাউন্ট  −৳${money(discount)}"
            discountBadge.visibility = if(discount>0) View.VISIBLE else View.GONE
            remove.visibility=if(appliedCoupon.isBlank())View.GONE else View.VISIBLE
        }
        zone.onItemSelectedListener=spinnerListener{updateSummary()}
        apply.setOnClickListener {
            val code=couponInput.text.toString().trim().uppercase(); if(code.isBlank()){toast("কুপন কোড লিখুন");return@setOnClickListener}
            lifecycleScope.launch { runCatching {
                val a=api.select("coupons","select=*&code=eq.${Uri.encode(code)}&is_active=eq.true"); if(a.length()==0)throw IllegalArgumentException("অবৈধ কুপন কোড")
                val c=a.getJSONObject(0); val expiry=c.optString("expires_at")
                if(expiry.isNotBlank() && (parseIsoMillis(expiry)?.let{it<System.currentTimeMillis()} ?: (expiry.take(19)<SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.US).format(Date()))))throw IllegalArgumentException("কুপনের মেয়াদ শেষ")
                val usageLimit=c.optInt("usage_limit",0); if(usageLimit>0 && usageLimit<=c.optInt("used_count",0))throw IllegalArgumentException("কুপনের ব্যবহার সীমা শেষ")
                if(subtotal()<c.optDouble("min_order",0.0))throw IllegalArgumentException("ন্যূনতম অর্ডার ৳${money(c.optDouble("min_order"))}")
                discountPreview=couponDiscount(code,subtotal(),c); appliedCoupon=code; updateSummary(); toast("কুপন প্রয়োগ হয়েছে")
            }.onFailure { discountPreview=0.0; appliedCoupon=""; updateSummary(); toast(it.message?:"কুপন যাচাই ব্যর্থ") } }
        }
        remove.setOnClickListener { appliedCoupon=""; discountPreview=0.0; couponInput.text.clear(); updateSummary(); toast("কুপন সরানো হয়েছে") }
        redraw(); updateSummary()
        val cartDialog = AlertDialog.Builder(this).setTitle("আপনার কার্ট (${cart.values.sumOf { it.qty }})").setView(ScrollView(this).apply { addView(box) }).setPositiveButton("চেকআউট", null).setNegativeButton("বন্ধ",null).create()
        cartDialog.setOnShowListener {
            checkoutButton = cartDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            updateCheckoutAvailability()
            checkoutButton?.setOnClickListener {
                if (cart.isEmpty()) {
                    cartDialog.dismiss()
                    b.mainScroll.smoothScrollTo(0, 0)
                } else if (subtotal() >= settings.optDouble("min_order", 0.0)) {
                    cartDialog.dismiss()
                    showCheckout()
                }
            }
        }
        cartDialog.show()
    }
    private fun subtotal()=cart.values.sumOf{it.product.optDouble("price")*it.qty}
    private fun couponDiscount(code:String, sub:Double, c:JSONObject):Double { var d=if(c.optString("type")=="percent") (sub*c.optDouble("value")/100.0).roundToInt().toDouble() else c.optDouble("value"); if(c.has("max_discount")&&!c.isNull("max_discount")) d=minOf(d,c.optDouble("max_discount")); return d.coerceIn(0.0,sub) }
    private fun totals():Triple<Double,Double,Double> { val sub=subtotal(); val discount=if(appliedCoupon.isBlank())0.0 else discountPreview; val after=(sub-discount).coerceAtLeast(0.0); val free=settings.optDouble("free_delivery_threshold",0.0)>0&&after>=settings.optDouble("free_delivery_threshold",0.0); val delivery=if(free)0.0 else settings.optDouble(if(currentZone=="in")"del_in" else "del_out",if(currentZone=="in")60.0 else 120.0); val tax=(after*settings.optDouble("tax",0.0)/100.0).roundToInt().toDouble(); return Triple(delivery,tax,after+delivery+tax) }

    private fun showCheckout() {
        if (settings.optBoolean("maintenance", false)) { toast("দোকানটি সাময়িক রক্ষণাবেক্ষণে আছে"); return }
        if(cart.isEmpty())return
        if(!toggles.optBoolean("f_s_guest_checkout",true)&&api.userId==null){showAuth();return}
        val form=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,8,20,4)}
        fun field(h:String,type:Int=InputType.TYPE_CLASS_TEXT):EditText=EditText(this).apply{hint=h;inputType=type;form.addView(this)}
        val name=field("আপনার নাম");val phone=field("মোবাইল নম্বর (01XXXXXXXXX)",InputType.TYPE_CLASS_PHONE);val address=field("সম্পূর্ণ ঠিকানা");val notes=field("অর্ডার নোট (ঐচ্ছিক)");val coupon=field("কুপন কোড (ঐচ্ছিক)").apply{setText(appliedCoupon)}
        val applyCouponButton=Button(this).apply{text="কুপন প্রয়োগ করুন"};form.addView(applyCouponButton)
        if(api.userId!=null){name.setText(userData.optString("name"));phone.setText(userData.optString("phone"));address.setText(userData.optString("address"))}
        val zone=Spinner(this);zone.adapter=spinnerAdapter(listOf("ঢাকার ভিতরে","ঢাকার বাইরে"));form.addView(label("ডেলিভারি এলাকা"));form.addView(zone)
        val payMethods=mutableListOf<Pair<String,String>>();if(settings.optBoolean("cod_on",true))payMethods.add("cod" to "ক্যাশ অন ডেলিভারি");if(settings.optBoolean("bkash_on",true))payMethods.add("bkash" to "বিকাশ");if(settings.optBoolean("nagad_on",true))payMethods.add("nagad" to "নগদ");if(payMethods.isEmpty()){toast("Admin থেকে কোনো পেমেন্ট পদ্ধতি চালু নেই");return}
        val pay=Spinner(this);pay.adapter=spinnerAdapter(payMethods.map{it.second});form.addView(label("পেমেন্ট পদ্ধতি"));form.addView(pay)
        val paymentNumber = TextView(this).apply { setPadding(0, 6, 0, 2); setTextColor(getColor(R.color.text_main)); visibility = View.GONE }
        val copyPaymentNumber = Button(this).apply { text = "পেমেন্ট নম্বর কপি করুন"; visibility = View.GONE }
        form.addView(paymentNumber); form.addView(copyPaymentNumber)
        val trx=field("বিকাশ/নগদ TrxID (অনলাইন পেমেন্টে)")
        val wallet=CheckBox(this).apply{text="ওয়ালেট ব্যবহার করুন (৳${money(userData.optDouble("wallet_balance"))})";isEnabled=api.userId!=null&&userData.optDouble("wallet_balance")>0};form.addView(wallet)
        val summary=TextView(this).apply{setPadding(0,8,0,8);setTextColor(getColor(R.color.gold_dark))};form.addView(summary)
        val waAvailable=toggles.optBoolean("f_s_wa_checkout",true)&&settings.optString("whatsapp",settings.optString("phone")).filter{it.isDigit()}.length>=10
        if(waAvailable)form.addView(TextView(this).apply{text="WhatsApp-এ অর্ডার পাঠানো যাবে";setTextColor(getColor(R.color.success));setPadding(0,4,0,4)})
        fun updateSummary(){
            currentZone=if(zone.selectedItemPosition==0)"in" else "out"
            selectedPayment=payMethods[pay.selectedItemPosition.coerceIn(0,payMethods.lastIndex)].first
            walletEnabled=wallet.isChecked
            val after=(subtotal()-discountPreview).coerceAtLeast(0.0)
            val free=settings.optDouble("free_delivery_threshold",0.0)>0&&after>=settings.optDouble("free_delivery_threshold",0.0)
            val delivery=if(free)0.0 else settings.optDouble(if(currentZone=="in")"del_in" else "del_out",if(currentZone=="in")60.0 else 120.0)
            val tax=(after*settings.optDouble("tax",0.0)/100.0).roundToInt().toDouble()
            val total=after+delivery+tax
            val w=if(walletEnabled)minOf(userData.optDouble("wallet_balance"),total)else 0.0
            val number=when(selectedPayment){"bkash"->settings.optString("bkash");"nagad"->settings.optString("nagad");else->""}
            paymentNumber.visibility=if(number.isNotBlank())View.VISIBLE else View.GONE
            copyPaymentNumber.visibility=if(number.isNotBlank())View.VISIBLE else View.GONE
            trx.visibility=if(selectedPayment=="cod")View.GONE else View.VISIBLE
            paymentNumber.text=if(number.isNotBlank())"${if(selectedPayment=="bkash")"বিকাশ" else "নগদ"} নম্বর: $number" else ""
            summary.text="সাবটোটাল: ৳${money(subtotal())}\nকুপন ছাড়: -৳${money(discountPreview)}\nডেলিভারি: ৳${money(delivery)}\nট্যাক্স: ৳${money(tax)}\nসর্বমোট: ৳${money(total)}\nওয়ালেট: -৳${money(w)}\nপরিশোধযোগ্য: ৳${money(total-w)}"
        }
        copyPaymentNumber.setOnClickListener {
            val number=if(selectedPayment=="bkash")settings.optString("bkash") else settings.optString("nagad")
            if(number.isNotBlank()){
                val clipboard=getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Payment number",number))
                toast("পেমেন্ট নম্বর কপি হয়েছে")
            }
        }
        zone.onItemSelectedListener=spinnerListener{updateSummary()};pay.onItemSelectedListener=spinnerListener{updateSummary()};wallet.setOnCheckedChangeListener{_,_->updateSummary()};applyCouponButton.setOnClickListener{val code=coupon.text.toString().trim().uppercase();if(code.isBlank()){toast("কুপন কোড লিখুন")}else lifecycleScope.launch{runCatching{val a=api.select("coupons","select=*&code=eq.${Uri.encode(code)}&is_active=eq.true");if(a.length()==0)throw IllegalArgumentException("অবৈধ কুপন কোড");val c=a.getJSONObject(0);if(c.optString("expires_at").isNotBlank()&&(parseIsoMillis(c.optString("expires_at"))?.let{it<System.currentTimeMillis()} ?: (c.optString("expires_at").take(19)<SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.US).format(Date()))))throw IllegalArgumentException("কুপনের মেয়াদ শেষ");val usageLimit=c.optInt("usage_limit",0);if(usageLimit>0&&usageLimit<=c.optInt("used_count",0))throw IllegalArgumentException("কুপনের ব্যবহার সীমা শেষ");if(subtotal()<c.optDouble("min_order",0.0))throw IllegalArgumentException("ন্যূনতম অর্ডার ৳${money(c.optDouble("min_order"))}");discountPreview=couponDiscount(code,subtotal(),c);appliedCoupon=code;updateSummary();toast("কুপন প্রয়োগ হয়েছে")} .onFailure{discountPreview=0.0;appliedCoupon="";updateSummary();toast(it.message?:"কুপন যাচাই ব্যর্থ")}}};updateSummary()
        val d=AlertDialog.Builder(this).setTitle("অর্ডার নিশ্চিত করুন").setView(ScrollView(this).apply{addView(form)}).setPositiveButton("অর্ডার নিশ্চিত করুন",null).setNeutralButton(if(waAvailable)"WhatsApp" else "",null).setNegativeButton("বাতিল",null).create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { val nm=name.text.toString().trim();val ph=phone.text.toString().filter{it.isDigit()};val ad=address.text.toString().trim();if(nm.isBlank()||!Regex("^01[3-9][0-9]{8}$").matches(ph)||ad.isBlank()){toast("নাম, সঠিক ১১ ডিজিটের নম্বর ও ঠিকানা দিন");return@setOnClickListener};if(selectedPayment!="cod"&&trx.text.isNullOrBlank()){toast("TrxID দিন");return@setOnClickListener};lifecycleScope.launch{placeOrder(nm,ph,ad,notes.text.toString(),currentZone,selectedPayment,trx.text.toString(),coupon.text.toString().trim().uppercase(),wallet.isChecked,d)} }
            if(waAvailable)d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener{val nm=name.text.toString().trim();val ph=phone.text.toString().filter{it.isDigit()};val ad=address.text.toString().trim();if(nm.isBlank()||!Regex("^01[3-9][0-9]{8}$").matches(ph)||ad.isBlank()){toast("নাম, সঠিক ফোন ও ঠিকানা দিন");return@setOnClickListener};openWhatsApp(nm,ph,ad,notes.text.toString());d.dismiss()}
        };d.show()
    }

    private suspend fun placeOrder(name:String,phone:String,address:String,notes:String,zone:String,method:String,trx:String,couponCode:String,useWallet:Boolean,dialog:AlertDialog) {
        try {
            if (settings.optBoolean("maintenance", false)) { toast("দোকানটি সাময়িক রক্ষণাবেক্ষণে আছে"); return }
            if(api.userId!=null&&userData.optBoolean("is_blocked",false)){toast("আপনার অ্যাকাউন্ট ব্লক করা হয়েছে");return}
            val sub=subtotal();var discount=0.0
            if(couponCode.isNotBlank()){val cs=api.select("coupons","select=*&code=eq.${Uri.encode(couponCode)}&is_active=eq.true");if(cs.length()==0){toast("কুপন সঠিক নয় বা বন্ধ");return};val c=cs.getJSONObject(0);if(sub<c.optDouble("min_order",0.0)){toast("এই কুপনের ন্যূনতম অর্ডার ৳${money(c.optDouble("min_order"))}");return};val usageLimit=c.optInt("usage_limit",0);if(usageLimit>0&&usageLimit<=c.optInt("used_count",0)){toast("কুপনের ব্যবহার সীমা শেষ");return};val exp=c.optString("expires_at");if(exp.isNotBlank()&&(parseIsoMillis(exp)?.let{it<System.currentTimeMillis()} ?: (exp.take(19)<SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.US).format(Date())))){toast("কুপনের মেয়াদ শেষ");return};discount=couponDiscount(couponCode,sub,c)}
            val after=(sub-discount).coerceAtLeast(0.0);val free=settings.optDouble("free_delivery_threshold",0.0)>0&&after>=settings.optDouble("free_delivery_threshold",0.0);val del=if(free)0.0 else settings.optDouble(if(zone=="in")"del_in" else "del_out",if(zone=="in")60.0 else 120.0);val tax=(after*settings.optDouble("tax",0.0)/100.0).roundToInt().toDouble();val total=after+del+tax;val walletUsed=if(useWallet&&api.userId!=null)minOf(userData.optDouble("wallet_balance"),total)else 0.0
            if(total<settings.optDouble("min_order",0.0)){toast("ন্যূনতম অর্ডার ৳${money(settings.optDouble("min_order"))}");return}
            val items=JSONArray();cart.values.forEach{items.put(JSONObject().put("id",it.product.optString("id")).put("qty",it.qty).put("selectedSize",it.size).put("selectedColor",it.color))}
            val payload=JSONObject().put("p_customer_name",name).put("p_phone",phone).put("p_address",address).put("p_notes",notes).put("p_delivery_zone",zone).put("p_delivery_charge",del).put("p_tax",tax).put("p_wallet_used",walletUsed).put("p_payment_method",method).put("p_trx_id",trx).put("p_coupon_code",couponCode).put("p_items",items)
            val result=api.rpc("place_order_atomic",payload);val orderId=result.trim().trim('"')
            if (api.userId == null && orderId.isNotBlank()) {
                guestOrderIds.remove(orderId)
                guestOrderIds.add(0, orderId)
                while (guestOrderIds.size > 15) guestOrderIds.removeAt(guestOrderIds.lastIndex)
            }
            cart.clear();appliedCoupon="";discountPreview=0.0;walletEnabled=false;saveLocal();dialog.dismiss();if(api.userId!=null)refreshUserData()
            AlertDialog.Builder(this).setTitle("অর্ডার সফল হয়েছে").setMessage("আপনার অর্ডার গ্রহণ করা হয়েছে।\nঅর্ডার আইডি: ${orderId.take(18)}").setPositiveButton("আমার অর্ডার"){_,_->showOrders()}.setNegativeButton("ঠিক আছে",null).show()
        }catch(e:Exception){toast("অর্ডার করা যায়নি: ${e.message}")}
    }

    private fun openWhatsApp(name:String,phone:String,address:String,notes:String) {
        if (settings.optBoolean("maintenance", false)) { toast("দোকানটি সাময়িক রক্ষণাবেক্ষণে আছে"); return }
        val number=settings.optString("whatsapp").ifBlank{settings.optString("phone")}.filter{it.isDigit()};if(number.isBlank()){toast("WhatsApp নম্বর সেট করা নেই");return}
        val sub=subtotal()
        val discount=discountPreview.coerceIn(0.0,sub)
        val afterDiscount=(sub-discount).coerceAtLeast(0.0)
        val threshold=settings.optDouble("free_delivery_threshold",0.0)
        val freeDelivery=threshold>0.0 && afterDiscount>=threshold
        val zoneDel=if(freeDelivery)0.0 else settings.optDouble(if(currentZone=="in")"del_in" else "del_out",if(currentZone=="in")60.0 else 120.0)
        val tax=(afterDiscount*settings.optDouble("tax",0.0)/100.0).roundToInt().toDouble()
        val total=afterDiscount+zoneDel+tax
        val paymentLabel=when(selectedPayment){"bkash"->"বিকাশ";"nagad"->"নগদ";else->"ক্যাশ অন ডেলিভারি"}
        val lines=cart.values.mapIndexed{i,l->"${i+1}. ${l.product.optString("title")}${if(l.size.isNotBlank())" | সাইজ: ${l.size}" else ""}${if(l.color.isNotBlank())" | কালার: ${l.color}" else ""} × ${l.qty} = ৳${money(l.product.optDouble("price")*l.qty)}"}
        val msg=(listOf("নতুন অর্ডার — ${settings.optString("store_name","MM BAZAAR")}","")+lines+listOf("","সাবটোটাল: ৳${money(sub)}")+(if(discount>0)listOf("ডিসকাউন্ট: -৳${money(discount)}")else emptyList())+listOf("ডেলিভারি: ${if(freeDelivery)"ফ্রি" else "৳${money(zoneDel)}"}","ট্যাক্স: ৳${money(tax)}","সর্বমোট: ৳${money(total)}","","নাম: $name","মোবাইল: $phone","ঠিকানা: $address",if(notes.isNotBlank())"নোট: $notes" else "","পেমেন্ট: $paymentLabel")).filter{it.isNotBlank()}.joinToString("\n")
        val uri=Uri.parse("https://wa.me/88${number.removePrefix("88")}?text=${URLEncoder.encode(msg,"UTF-8")}");runCatching{startActivity(Intent(Intent.ACTION_VIEW,uri))}.onFailure{toast("WhatsApp খোলা যায়নি")}
    }

    private fun showOrders() {
        if (api.userId == null && guestOrderIds.isEmpty()) { showAuth(); return }
        lifecycleScope.launch {
            try {
                val rows = if (api.userId != null) {
                    api.select("orders", "select=*&user_id=eq.${api.userId}&order=created_at.desc")
                } else {
                    JSONArray(api.rpc("get_guest_orders_by_ids", JSONObject().put("p_ids", JSONArray(guestOrderIds))))
                }
                var ordersDialog: AlertDialog? = null
                val content = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 8, 14, 8) }
                if (rows.length() == 0) {
                    content.addView(TextView(this@MainActivity).apply { text = "আপনি এখনো কোনো অর্ডার করেননি।"; setPadding(12, 24, 12, 24); gravity = Gravity.CENTER; setTextColor(getColor(R.color.text_secondary)) })
                } else {
                    for (i in 0 until rows.length()) {
                        val order = rows.getJSONObject(i)
                        val orderId = order.optString("id")
                        val status = order.optString("status", "unknown")
                        val items = order.optJSONArray("items")
                        val itemSummary = if (items == null) "" else (0 until items.length()).joinToString(", ") { index ->
                            val item = items.optJSONObject(index) ?: JSONObject()
                            "${item.optString("title", "পণ্য")} × ${item.optInt("qty", 1)}"
                        }
                        val card = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 12, 14, 12); background = getDrawable(R.drawable.bg_card); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 } }
                        card.addView(TextView(this@MainActivity).apply { text = "#${orderId.take(8)}   •   $status"; textSize = 14f; setTextColor(getColor(R.color.text_main)); setTypeface(null, android.graphics.Typeface.BOLD) })
                        card.addView(TextView(this@MainActivity).apply { text = "তারিখ: ${order.optString("created_at")}\nমোট: ৳${money(order.optDouble("total_amount"))}"; textSize = 12f; setTextColor(getColor(R.color.text_secondary)); setPadding(0, 5, 0, 5) })
                        if (itemSummary.isNotBlank()) card.addView(TextView(this@MainActivity).apply { text = "আইটেম: $itemSummary"; textSize = 12f; setTextColor(getColor(R.color.text_secondary)) })
                        val courier = order.optJSONObject("courier_info")
                        val tracking = courier?.optString("trackingId").orEmpty()
                        if (tracking.isNotBlank()) card.addView(TextView(this@MainActivity).apply { text = "ট্র্যাকিং: ${courier?.optString("service").orEmpty()} - $tracking"; textSize = 12f; setTextColor(getColor(R.color.success)); setPadding(0, 6, 0, 0) })
                        if (status.equals("pending", true)) {
                            card.addView(Button(this@MainActivity).apply {
                                text = "অর্ডার বাতিল করুন"
                                setOnClickListener {
                                    AlertDialog.Builder(this@MainActivity).setMessage("আপনি কি এই অর্ডারটি বাতিল করতে চান?")
                                        .setPositiveButton("হ্যাঁ, বাতিল করুন") { _, _ ->
                                            lifecycleScope.launch {
                                                runCatching { api.rpc("cancel_order_atomic", JSONObject().put("p_order_id", orderId)) }
                                                    .onSuccess { ordersDialog?.dismiss(); toast("অর্ডার বাতিলের অনুরোধ সফল"); showOrders() }
                                                    .onFailure { toast("অর্ডার বাতিল হয়নি: ${it.message}") }
                                            }
                                        }.setNegativeButton("ফিরে যান", null).show()
                                }
                            })
                        }
                        content.addView(card)
                    }
                }
                ordersDialog = AlertDialog.Builder(this@MainActivity).setTitle("আমার অর্ডারসমূহ")
                    .setView(ScrollView(this@MainActivity).apply { addView(content) })
                    .setPositiveButton("বন্ধ", null)
                    .setNeutralButton("ফোন দিয়ে ট্র্যাক") { _, _ -> trackByPhone() }
                    .create()
                ordersDialog?.show()
                ordersDialog?.window?.setLayout(-1, (resources.displayMetrics.heightPixels * .78f).toInt())
            } catch (e: Exception) {
                toast("অর্ডার লোড হয়নি: ${e.message}")
            }
        }
    }
    private fun cancelOrderPrompt(){val input=EditText(this).apply{hint="সম্পূর্ণ Order ID"};AlertDialog.Builder(this).setTitle("অর্ডার বাতিল").setMessage("শুধু Pending অর্ডার বাতিল করা যাবে").setView(input).setPositiveButton("বাতিল নিশ্চিত"){_,_->lifecycleScope.launch{runCatching{api.rpc("cancel_order_atomic",JSONObject().put("p_order_id",input.text.toString().trim()));toast("বাতিলের অনুরোধ সফল")} .onFailure{toast("বাতিল হয়নি: ${it.message}")}}}.setNegativeButton("ফিরে যান",null).show()}
    private fun trackByPhone() {
        val input = EditText(this).apply {
            hint = "01XXXXXXXXX"
            inputType = InputType.TYPE_CLASS_PHONE
        }
        val dialog = AlertDialog.Builder(this).setTitle("অর্ডার ট্র্যাক করুন")
            .setMessage("অর্ডারের সময় ব্যবহৃত ১১ ডিজিটের মোবাইল নম্বর দিন")
            .setView(input).setPositiveButton("খুঁজুন", null).setNegativeButton("বাতিল", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val phone = input.text.toString().filter { it.isDigit() }
                if (!Regex("^01[3-9][0-9]{8}$").matches(phone)) {
                    toast("সঠিক ১১ ডিজিটের নম্বর দিন")
                    return@setOnClickListener
                }
                dialog.dismiss()
                lifecycleScope.launch {
                    runCatching {
                        val rows = JSONArray(api.rpc("track_orders_by_phone", JSONObject().put("p_phone", phone)))
                        val content = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(14, 8, 14, 8)
                        }
                        if (rows.length() == 0) {
                            content.addView(TextView(this@MainActivity).apply {
                                text = "কোনো অর্ডার পাওয়া যায়নি"
                                gravity = Gravity.CENTER
                                setPadding(12, 24, 12, 24)
                            })
                        } else {
                            for (i in 0 until rows.length()) {
                                val order = rows.getJSONObject(i)
                                val items = order.optJSONArray("items")
                                val summary = if (items == null) "" else (0 until items.length()).joinToString(", ") { index ->
                                    val item = items.optJSONObject(index) ?: JSONObject()
                                    "${item.optString("title", "পণ্য")} × ${item.optInt("qty", 1)}"
                                }
                                val card = LinearLayout(this@MainActivity).apply {
                                    orientation = LinearLayout.VERTICAL
                                    setPadding(12, 10, 12, 10)
                                    background = getDrawable(R.drawable.bg_card)
                                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 }
                                }
                                card.addView(TextView(this@MainActivity).apply {
                                    text = "#${order.optString("id").take(8)}  •  ${order.optString("status")}" 
                                    setTextColor(getColor(R.color.text_main))
                                    textSize = 14f
                                })
                                card.addView(TextView(this@MainActivity).apply {
                                    text = "তারিখ: ${order.optString("created_at")}\nমোট: ৳${money(order.optDouble("total_amount"))}"
                                    setTextColor(getColor(R.color.text_secondary))
                                    textSize = 12f
                                    setPadding(0, 5, 0, 5)
                                })
                                if (summary.isNotBlank()) card.addView(TextView(this@MainActivity).apply {
                                    text = "আইটেম: $summary"
                                    setTextColor(getColor(R.color.text_secondary))
                                    textSize = 12f
                                })
                                val courier = order.optJSONObject("courier_info")
                                val tracking = courier?.optString("trackingId").orEmpty()
                                if (tracking.isNotBlank()) card.addView(TextView(this@MainActivity).apply {
                                    text = "ট্র্যাকিং: ${courier?.optString("service").orEmpty()} - $tracking"
                                    setTextColor(getColor(R.color.success))
                                    setPadding(0, 6, 0, 0)
                                })
                                content.addView(card)
                            }
                        }
                        AlertDialog.Builder(this@MainActivity).setTitle("অর্ডারের তথ্য")
                            .setView(ScrollView(this@MainActivity).apply { addView(content) })
                            .setPositiveButton("বন্ধ", null).show()
                    }.onFailure { toast("অর্ডার খুঁজতে সমস্যা হয়েছে: ${it.message}") }
                }
            }
        }
        dialog.show()
    }


    private fun showProfile(){
        if(api.userId==null){
            profileActions(false)
            return
        }
        lifecycleScope.launch {
            runCatching { val u=api.select("users","select=*&id=eq.${api.userId}"); if(u.length()>0) userData=u.getJSONObject(0) }
            val name=userData.optString("name",api.email?:"আমার অ্যাকাউন্ট")
            AlertDialog.Builder(this@MainActivity).setTitle(name)
                .setMessage("ইমেইল: ${api.email}\nমোবাইল: ${userData.optString("phone")}\nঠিকানা: ${userData.optString("address")}\n\nওয়ালেট: ৳${money(userData.optDouble("wallet_balance"))}\nLoyalty Points: ${userData.optInt("loyalty_points")}")
                .setItems(arrayOf("প্রোফাইল সম্পাদনা","আমার অর্ডার","অর্ডার ট্র্যাক করুন","কাস্টমার চ্যাট","রিটার্ন পলিসি","প্রাইভেসি পলিসি","আমাদের সম্পর্কে","ফেসবুক পেজ","ইউটিউব চ্যানেল","হটলাইন কল","লগআউট")){_,which->when(which){0->editProfile();1->showOrders();2->trackByPhone();3->openChat();4->showPolicy("policy_return","রিটার্ন পলিসি");5->showPolicy("policy_privacy","প্রাইভেসি পলিসি");6->showPolicy("policy_about","আমাদের সম্পর্কে");7->openExternal("https://www.facebook.com/profile.php?id=61596001466743");8->openExternal("https://www.youtube.com/@mm-bazaar");9->callHelpline();10->lifecycleScope.launch{runCatching{api.signOut();userData=JSONObject();toast("লগআউট হয়েছে")}.onFailure{toast(it.message?:"লগআউট ব্যর্থ")}}}}
                .setNegativeButton("বন্ধ",null).show()
        }
    }
    private fun profileActions(loggedIn:Boolean){
        val items = mutableListOf<String>()
        if (loggedIn) items.add("আমার অর্ডার") else {
            items.add("লগইন / রেজিস্ট্রেশন")
            items.add("আমার সমস্ত অর্ডার")
        }
        items.addAll(listOf("অর্ডার ট্র্যাক করুন","রিটার্ন পলিসি","প্রাইভেসি পলিসি","আমাদের সম্পর্কে","ফেসবুক পেজ","ইউটিউব চ্যানেল","হটলাইন কল"))
        AlertDialog.Builder(this).setTitle("প্রোফাইল").setItems(items.toTypedArray()){_,which->
            val selected = items[which]
            when(selected){
                "লগইন / রেজিস্ট্রেশন" -> showAuth()
                "আমার অর্ডার", "আমার সমস্ত অর্ডার" -> showOrders()
                "অর্ডার ট্র্যাক করুন" -> trackByPhone()
                "রিটার্ন পলিসি" -> showPolicy("policy_return","রিটার্ন পলিসি")
                "প্রাইভেসি পলিসি" -> showPolicy("policy_privacy","প্রাইভেসি পলিসি")
                "আমাদের সম্পর্কে" -> showPolicy("policy_about","আমাদের সম্পর্কে")
                "ফেসবুক পেজ" -> openExternal("https://www.facebook.com/profile.php?id=61596001466743")
                "ইউটিউব চ্যানেল" -> openExternal("https://www.youtube.com/@mm-bazaar")
                "হটলাইন কল" -> callHelpline()
            }
        }.show()
    }
    private fun openExternal(url:String){ runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { toast("লিংক খোলা যায়নি") } }
    private fun callHelpline(){ val phone=settings.optString("phone","01937175424").filter{it.isDigit()}; runCatching { startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:$phone"))) }.onFailure { toast("ফোন অ্যাপ খোলা যায়নি") } }
    private fun showPolicy(key:String,title:String){ AlertDialog.Builder(this).setTitle(title).setMessage(settings.optString(key).ifBlank{"তথ্য পাওয়া যায়নি"}).setPositiveButton("বন্ধ",null).show() }

    private fun editProfile(){val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,8,20,0)};val name=EditText(this).apply{hint="নাম";setText(userData.optString("name"))};val phone=EditText(this).apply{hint="মোবাইল";inputType=InputType.TYPE_CLASS_PHONE;setText(userData.optString("phone"))};val address=EditText(this).apply{hint="ঠিকানা";minLines=2;setText(userData.optString("address"))};box.addView(name);box.addView(phone);box.addView(address);val d=AlertDialog.Builder(this).setTitle("প্রোফাইল আপডেট").setView(box).setPositiveButton("সংরক্ষণ",null).setNegativeButton("বাতিল",null).create();d.setOnShowListener{d.getButton(-1).setOnClickListener{val ph=phone.text.toString().filter{it.isDigit()};if(!Regex("^01[3-9][0-9]{8}$").matches(ph)){toast("সঠিক ১১ ডিজিটের ফোন দিন");return@setOnClickListener};lifecycleScope.launch{runCatching{api.update("users","id=eq.${api.userId}",JSONObject().put("name",name.text.toString().trim()).put("phone",ph).put("address",address.text.toString().trim()));userData.put("name",name.text.toString().trim()).put("phone",ph).put("address",address.text.toString().trim());d.dismiss();toast("প্রোফাইল আপডেট হয়েছে")}.onFailure{toast("আপডেট হয়নি: ${it.message}")}}}};d.show()}
    private fun showAuth() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 8, 20, 0)
        }
        var signupMode = false
        val name = EditText(this).apply { hint = "নাম (নতুন অ্যাকাউন্ট)"; visibility = View.GONE }
        val phone = EditText(this).apply { hint = "মোবাইল (নতুন অ্যাকাউন্ট)"; inputType = InputType.TYPE_CLASS_PHONE; visibility = View.GONE }
        val email = EditText(this).apply { hint = "ইমেইল"; inputType = 33 }
        val pass = EditText(this).apply { hint = "পাসওয়ার্ড (কমপক্ষে ৬ অক্ষর)"; inputType = 129 }
        val forgot = TextView(this).apply {
            text = "পাসওয়ার্ড ভুলে গেছেন?"
            setTextColor(getColor(R.color.gold_dark))
            setPadding(0, 12, 0, 4)
            setOnClickListener {
                val address = email.text.toString().trim()
                if (address.isBlank()) toast("আগে ইমেইল লিখুন") else lifecycleScope.launch {
                    runCatching { api.resetPasswordForEmail(address); toast("পাসওয়ার্ড রিসেট লিংক পাঠানো হয়েছে") }
                        .onFailure { toast(it.message ?: "ব্যর্থ") }
                }
            }
        }
        box.addView(name); box.addView(phone); box.addView(email); box.addView(pass); box.addView(forgot)
        val dialog = AlertDialog.Builder(this).setTitle("লগইন / রেজিস্ট্রেশন")
            .setView(box).setPositiveButton("লগইন", null)
            .setNeutralButton("নতুন অ্যাকাউন্ট খুলুন", null)
            .setNegativeButton("বন্ধ", null).create()
        dialog.setOnShowListener {
            val submit = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val switch = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            fun updateMode() {
                name.visibility = if (signupMode) View.VISIBLE else View.GONE
                phone.visibility = if (signupMode) View.VISIBLE else View.GONE
                forgot.visibility = if (signupMode) View.GONE else View.VISIBLE
                submit.text = if (signupMode) "রেজিস্ট্রেশন করুন" else "লগইন করুন"
                switch.text = if (signupMode) "লগইনে ফিরে যান" else "নতুন অ্যাকাউন্ট খুলুন"
            }
            submit.setOnClickListener {
                doAuth(email.text.toString(), pass.text.toString(), name.text.toString(), phone.text.toString(), signupMode, dialog)
            }
            switch.setOnClickListener { signupMode = !signupMode; updateMode() }
            updateMode()
        }
        dialog.show()
    }
    private fun doAuth(email:String,password:String,name:String,phone:String,signup:Boolean,d:AlertDialog){lifecycleScope.launch{runCatching{if(signup){if(name.isBlank()||!Regex("^01[3-9][0-9]{8}$").matches(phone.filter{it.isDigit()}))throw IllegalArgumentException("নাম ও সঠিক ১১ ডিজিটের ফোন দিন");if(password.length<6)throw IllegalArgumentException("পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে");api.signUp(email.trim(),password,name.trim(),phone.filter{it.isDigit()})}else api.signIn(email.trim(),password);d.dismiss();refreshUserData();toast(if(signup)"অ্যাকাউন্ট তৈরি হয়েছে" else "লগইন সফল");showProfile()}.onFailure{toast(it.message?:"লগইন ব্যর্থ")}}}
    private fun openReview(p:JSONObject){
        if(api.userId==null){showAuth();return}
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,8,20,0)}
        val rating=RatingBar(this).apply{numStars=5;stepSize=1f;rating=5f}
        val text=EditText(this).apply{hint="আপনার মন্তব্য লিখুন";minLines=3;gravity=Gravity.TOP}
        box.addView(label("আপনার রেটিং"));box.addView(rating);box.addView(text)
        val dialog=AlertDialog.Builder(this).setTitle("রিভিউ লিখুন").setView(box).setPositiveButton("জমা দিন",null).setNegativeButton("বাতিল",null).create()
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            val comment=text.text.toString().trim()
            if(comment.isBlank()){toast("রিভিউ লিখুন");return@setOnClickListener}
            lifecycleScope.launch{runCatching{
                api.rpc("submit_review_atomic",JSONObject().put("p_product_id",p.optString("id")).put("p_user_name",userData.optString("name","গ্রাহক")).put("p_rating",rating.rating.toDouble()).put("p_text",comment))
                toast("রিভিউ জমা হয়েছে");dialog.dismiss()
            }.onFailure{toast("রিভিউ হয়নি: ${it.message}")}}
        }}
        dialog.show()
    }
    private fun refreshChatBadge() {
        val uid = api.userId
        if (uid.isNullOrBlank()) { b.tvHeaderChat.text = "💬"; return }
        lifecycleScope.launch {
            runCatching {
                val rows = api.select("chats", "select=unread_for_user&id=eq.$uid&limit=1")
                val unread = rows.length() > 0 && rows.optJSONObject(0)?.optBoolean("unread_for_user", false) == true
                b.tvHeaderChat.text = if (unread) "💬 1" else "💬"
                b.tvHeaderChat.contentDescription = if (unread) "কাস্টমার চ্যাট, নতুন মেসেজ আছে" else "কাস্টমার চ্যাট"
            }
        }
    }

    private fun openChat() {
        val uid = api.userId
        if (uid.isNullOrBlank()) { showAuth(); return }
        lifecycleScope.launch { runCatching { api.update("chats", "id=eq.$uid", JSONObject().put("unread_for_user", false)) }; refreshChatBadge() }
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,8,16,0)}
        val messages=TextView(this).apply{minHeight=180;setTextColor(getColor(R.color.text_main));text="চ্যাট লোড হচ্ছে…"}
        val input=EditText(this).apply{hint="মেসেজ লিখুন"}
        box.addView(ScrollView(this).apply{addView(messages)})
        box.addView(input)
        val d=AlertDialog.Builder(this).setTitle("কাস্টমার সাপোর্ট").setView(box).setPositiveButton("পাঠান",null).setNegativeButton("বন্ধ",null).create()
        d.setOnShowListener {
            d.getButton(-1).setOnClickListener {
                val msg=input.text.toString().trim()
                if(msg.isNotBlank()) lifecycleScope.launch {
                    runCatching {
                        val now=nowIso()
                        api.upsert("chats",JSONObject().put("id",uid).put("user_id",uid).put("user_name",userData.optString("name",api.email)).put("phone",userData.optString("phone")).put("email",api.email).put("last_message",msg).put("unread_for_admin",true).put("unread_for_user",false).put("updated_at",now))
                        api.insert("chat_messages",JSONObject().put("chat_id",uid).put("sender_id",uid).put("text",msg).put("timestamp",now))
                        input.text.clear(); loadChat(uid,messages)
                    }.onFailure{toast("মেসেজ পাঠানো যায়নি: ${it.message}")}
                }
            }
        }
        d.setOnDismissListener { activeChatView=null }
        d.show(); activeChatView=messages; loadChat(uid,messages)
    }

    private fun loadChat(uid:String,target:TextView){
        lifecycleScope.launch{runCatching{
            val a=api.select("chat_messages","select=*&chat_id=eq.$uid&order=timestamp.asc")
            target.text=(0 until a.length()).joinToString("\n\n"){i->val m=a.getJSONObject(i);(if(m.optString("sender_id")==uid)"আপনি: " else "সাপোর্ট: ")+m.optString("text")}.ifBlank{"এখনো কোনো মেসেজ নেই"}
        }.onFailure { target.text="মেসেজ লোড করা যায়নি" }}
    }

    private fun stringArray(a:JSONArray?):List<String> = if(a==null) emptyList() else (0 until a.length()).map{a.optString(it)}.filter{it.isNotBlank()}
    private fun spinnerAdapter(items:List<String>)=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,items)
    private fun label(s:String)=TextView(this).apply{text=s;textSize=13f;setTextColor(getColor(R.color.text_secondary));setPadding(0,8,0,2)}
    private fun money(n:Double)=String.format(Locale.US,"%,.0f",n)
    private fun nowIso()=SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(Date())
    private fun spinnerListener(action:()->Unit)=object:AdapterView.OnItemSelectedListener{override fun onNothingSelected(parent:AdapterView<*>?){};override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long)=action()}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}
