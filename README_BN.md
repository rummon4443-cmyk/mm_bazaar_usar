# MM BAZAAR Android User App

এটি Kotlin + Android XML দিয়ে তৈরি একটি Android Native project। এখানে WebView ব্যবহার করা হয়নি। Supabase project URL এবং publishable key আগের HTML user app-এর একই project থেকে নেওয়া হয়েছে।

## Android Studio-তে চালানো
1. ZIP extract করুন।
2. Android Studio থেকে `MMBazaarUser` project folder open করুন।
3. Gradle sync শেষ হতে দিন (JDK 17, Android SDK 35 প্রয়োজন)।
4. `Build > Build Bundle(s) / APK(s) > Build APK(s)` নির্বাচন করুন।

GitHub Actions workflow `.github/workflows/build_apk.yml` দিয়েও Debug APK build করা যায়।

## গুরুত্বপূর্ণ অবস্থা
এই ZIP-কে এখনো HTML-এর ১০০% feature parity সম্পন্ন release বলা হচ্ছে না। `FEATURE_PARITY_BN.md`-এ বর্তমান বাস্তবায়ন ও বাকি কাজ আলাদা করে লেখা আছে। এই পরিবেশে Android SDK/Gradle build environment নেই, তাই APK compile করে পরীক্ষা করা সম্ভব হয়নি।

## Backend
- Supabase URL ও publishable key: `app/src/main/java/com/mmbazaar/user/data/Supabase.kt`
- Settings ও feature toggles: `settings` table-এর `global_config` এবং `feature_toggles` row
- Existing RPC usage: `place_order_atomic`, `cancel_order_atomic`, `track_orders_by_phone`, `submit_review_atomic`, etc.

কোনো service-role key এই অ্যাপে রাখা হয়নি।


## পরবর্তী সংশোধন (Build সফল হওয়ার পর)

এই আপডেটে:
- `global_config` এবং `feature_toggles`-এর reference defaults-এর সঙ্গে remote JSON merge করা হয়েছে; Admin-এর partial settings পাঠালেও default key হারাবে না।
- Maintenance Mode-এ নতুন Cart Add, Checkout, Order এবং WhatsApp Checkout বন্ধ করা হয়েছে।
- Admin সব payment method বন্ধ করলে COD-কে জোর করে চালু করা হয় না; checkout বন্ধ থাকে।
- bKash/Nagad চালু থাকলেও নম্বর ফাঁকা থাকলে সেই method দেখানো হয় না এবং order submit-এ পুনরায় যাচাই হয়।
- Wishlist toggle বন্ধ থাকলে bottom navigation ও wishlist page access বন্ধ।
- Chat header badge, unread state mark-as-read, foreground notification channel এবং Realtime subscription-এ `chats`, `chat_messages`, `reviews` যুক্ত।
- Phoenix heartbeat ও reconnect backoff যোগ করা হয়েছে।
- Chat message timestamp UTC-তে তৈরি হয়।

**সীমাবদ্ধতা:** Background/terminated app push notification এখনো FCM + Firebase configuration + server-side sender ছাড়া সম্ভব নয়। Supabase RLS/Realtime publication এবং বাস্তব Admin setting পরিবর্তন করে end-to-end test করতে হবে। এই পরিবেশে Android SDK নেই, তাই এই নতুন আপডেটের APK build এখানে চালানো হয়নি।
