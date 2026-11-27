# MM BAZAAR Android User App

এটি Kotlin + Android XML দিয়ে তৈরি Native Android project। WebView ব্যবহার করা হয়নি। Supabase project URL এবং publishable key আগের HTML user app-এর একই project থেকে নেওয়া হয়েছে.

## Android Studio-তে চালানো

1. ZIP extract করুন।
2. Android Studio থেকে `mm_bazaar_usar-main` project folder open করুন।
3. JDK 17 এবং Android SDK 35 install/sync করুন।
4. `Build > Build Bundle(s) / APK(s) > Build APK(s)` নির্বাচন করুন।

GitHub Actions workflow `.github/workflows/build_apk.yml` এখন `main`/`master` branch-এ push, pull request এবং manual dispatch-এ Debug APK build করবে। Build সফল হলে `MM-BAZAAR-User-Debug-APK` artifact পাওয়া যাবে.

## Backend

- Supabase URL এবং publishable key: `app/src/main/java/com/mmbazaar/user/data/Supabase.kt`
- Store settings ও feature toggles: `settings` table-এর `global_config` এবং `feature_toggles` row
- Existing RPC usage: `place_order_atomic`, `cancel_order_atomic`, `track_orders_by_phone`, `submit_review_atomic` ইত্যাদি
- অ্যাপে কোনো service-role key রাখা হয়নি.

## Notification সীমাবদ্ধতা

Customer chat-এর foreground Realtime notification যুক্ত আছে। Background বা app বন্ধ থাকলে push notification পেতে Firebase Cloud Messaging (FCM), Firebase project configuration এবং server-side sender/Edge Function setup করতে হবে।

## Build ও test status

এই ZIP তৈরির environment-এ Android SDK/Gradle ছিল না, তাই APK compile করে নিশ্চিত করা যায়নি। XML parsing check করা হয়েছে। GitHub Actions-এ push করে build result পরীক্ষা করুন। Supabase RLS/Realtime publication, Admin settings এবং checkout/payment edge cases বাস্তব project ও Android device-এ পরীক্ষা করা প্রয়োজন। বিস্তারিত তালিকা `FEATURE_PARITY_BN.md`-এ আছে.
