# MM BAZAAR — Native Android User App

এটি Kotlin + XML দিয়ে তৈরি Android User App project। **এই প্রজেক্টে WebView নেই এবং HTML User App Android UI হিসেবে চালানো হয় না।** হোম স্ক্রিন, পণ্য তালিকা, পণ্যের বিবরণ, ভ্যারিয়েন্ট নির্বাচন, কার্ট, চেকআউট, প্রোফাইল ও অন্যান্য ডায়ালগ Android native view দিয়ে তৈরি।

## সংযোগ

- একই Supabase project URL: `https://xayxwilczioscmldqxxj.supabase.co`
- একই publishable key, যা আগের User App HTML-এ ছিল
- আগের `products`, `categories`, `banners`, `settings`, `users`, `coupons`, `orders`, `reviews`, `chats`, `chat_messages` টেবিল ব্যবহার
- অর্ডার/বাতিল/রিভিউ/গেস্ট ট্র্যাকিংয়ের জন্য আগের RPC: `place_order_atomic`, `cancel_order_atomic`, `submit_review_atomic`, `track_orders_by_phone`, `get_guest_orders_by_ids`
- নতুন database/table/schema তৈরি করার কোনো migration এই project-এ নেই
- `service_role` key রাখা হয়নি

## Native UI-তে যুক্ত ফিচার

- হোম, ক্যাটাগরি, পণ্য সার্চ ও পণ্য গ্রিড
- Admin-এর `global_config` এবং `feature_toggles` সেটিংস পড়া
- Admin-নিয়ন্ত্রিত ব্যানার, marquee, flash sale, wishlist, guest checkout, size/color toggle
- পণ্যের size/color এবং `variant_stock` অনুযায়ী স্টক যাচাই
- ভ্যারিয়েন্ট আলাদা রেখে কার্টে পণ্য/পরিমাণ সংরক্ষণ
- কার্ট, দাম ফিল্টার, স্টক ফিল্টার, দাম অনুযায়ী সাজানো
- COD, bKash, Nagad এবং transaction ID
- কুপন কোডের eligibility/discount backend RPC-তে পাঠানো
- Wallet balance ও Loyalty Points দেখানো; Wallet ব্যবহার করলে আগের `place_order_atomic`-এ `p_wallet_used` পাঠানো হয়। Client থেকে wallet balance সরাসরি debit করা হয় না
- Admin-এর `users.wallet_balance`, `users.loyalty_points`, `users.is_blocked` ডেটা ব্যবহার
- WhatsApp checkout, Admin-এর `whatsapp`/`phone` নম্বর ব্যবহার করে
- লগইন, রেজিস্ট্রেশন, password reset, profile edit, logout
- অর্ডার তালিকা, guest order, ফোন দিয়ে tracking, cancellation
- Product reviews এবং customer support chat
- Supabase Realtime websocket subscription; পাশাপাশি ৬০ সেকেন্ডের refresh fallback

## Build করে APK নেওয়া

1. ZIP Extract করুন।
2. `MM-Bazaar-User-App-Native` folder-এর ভেতরের project GitHub repository-র root-এ upload করুন (folder-এর ভেতরের ফাইলগুলো root-এ রাখবেন)।
3. GitHub-এর **Actions** tab খুলুন।
4. `Build MM BAZAAR User APK` workflow চালান।
5. Build সফল হলে `MM-BAZAAR-User-Debug-APK` artifact থেকে APK নামান।

Android Studio-তেও project খুলে `:app:assembleDebug` চালানো যাবে। প্রয়োজন JDK 17, Android SDK 35, Gradle 8.9।

## গুরুত্বপূর্ণ সীমাবদ্ধতা ও যাচাই

- Firebase Android `google-services.json` দেওয়া হয়নি। তাই Native Firebase Cloud Messaging push notification এই project-এ চালু নেই। আগে পাওয়া Google Site Verification HTML Firebase config নয়।
- Supabase Realtime কাজ করতে project-এর Realtime publication-এ সংশ্লিষ্ট টেবিলগুলো enabled এবং RLS policy সঠিক থাকতে হবে। WebSocket সংযোগ ব্যর্থ হলে ৬০ সেকেন্ডে data refresh fallback আছে।
- Checkout, coupon, stock, wallet-এর চূড়ান্ত নিরাপত্তা ও হিসাব Supabase-এর বিদ্যমান atomic RPC-এর ওপর নির্ভর করে। এই Android client কোনো backend financial rule নিজে পরিবর্তন করে না।
- এখানে Android SDK/Gradle build runner নেই, তাই এই পরিবেশে APK compile বা বাস্তব Android ফোনে install test করা সম্ভব হয়নি। Kotlin syntax ও XML well-formedness পরীক্ষা করা হয়েছে; live Supabase account দিয়ে end-to-end test করা হয়নি।
- মূল HTML User App-এর সব ছোটখাটো UI/UX সুবিধা এখনো ১:১ native rewrite হয়েছে বলে দাবি করা হচ্ছে না। Native UI-তে উপরে তালিকাভুক্ত মূল shopping flow যুক্ত আছে; Firebase push এবং HTML-এর কিছু অতিরিক্ত presentation/utility অংশ আলাদা কাজ হিসেবে বাকি থাকতে পারে।
