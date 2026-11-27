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

## সর্বশেষ Profile / Chat / Reviews আপডেট

Guest ও signed-in profile options, policy/social/hotline links, chat unread badge, foreground notification, chat realtime subscription/heartbeat এবং product review/Admin reply display যুক্ত করা হয়েছে। বিস্তারিত schema ও সীমাবদ্ধতা `PROFILE_CHAT_REVIEWS_IMPLEMENTATION_BN.md` ফাইলে দেখুন। Background push notification-এর জন্য FCM ও server-side configuration এখনো প্রয়োজন।


## Admin Settings Sync আপডেট

Admin App-এর `global_config` ও `feature_toggles` settings row-এর সঙ্গে Native User App sync-এর বিবরণ `ADMIN_SETTINGS_PARITY_BN.md` ফাইলে আছে। Admin থেকে feature toggle বা store settings বদলালে Supabase Realtime subscription দিয়ে UI refresh হয়; connection না থাকলে ৬০ সেকেন্ডে fallback refresh হয়।
