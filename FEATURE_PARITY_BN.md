# MM BAZAAR HTML বনাম Android Native Feature Audit

**Reference:** ব্যবহারকারীর দেওয়া `index.html` এবং এই Android project-এর Kotlin/XML source.

এই Android app WebView নয়; এটি Kotlin + native Android XML UI. তাই HTML/PWA-র browser-only implementation-গুলোকে হুবহু কপি না করে প্রয়োজনমতো Native সমতুল্য ব্যবহার করা হয়েছে.

## Android Native-এ থাকা ফিচার

- একই Supabase project-এর REST/Auth সংযোগ, session refresh এবং local session persistence
- Products, categories, banners, settings ও feature toggles লোড; Supabase Realtime refresh
- Product listing, smart search (বাংলা/ইংরেজি synonym), voice search, recent search history
- Category/catalog screen, category filter, price/stock filter, sorting ও load-more pagination
- Product details, size/color selection, variant stock check, product image gallery, image zoom, product share ও deep links
- Cart, quantity control, local cart persistence, swipe-to-delete ও undo Snackbar, wishlist
- Coupon validation, coupon constraints, discount calculation, delivery zone, tax, free-delivery threshold/progress
- Checkout, COD/bKash/Nagad visibility, wallet preview, WhatsApp checkout, minimum order checks
- Order placement, order list, guest order tracking by phone, pending order cancel
- Sign in, sign up, password reset, profile edit, blocked-account check
- Profile actions: return/privacy/about policy, Facebook, YouTube, hotline
- Customer support chat, unread badge, message history and message send
- Product reviews display, review submission, Admin reply display
- Banner carousel/link, offer cards, flash sale listing/countdown, recently viewed products
- Admin-controlled feature visibility: brand, old price, size/color, banners, flash sale, guest checkout, hide out-of-stock, notice, WhatsApp checkout, wishlist
- Dark mode, scroll-to-top
- Product and page deep links

## এই আপডেটে করা অতিরিক্ত সংশোধন

- `global_config` এবং `feature_toggles`-এ default values-এর সঙ্গে remote JSON merge; Admin শুধু কয়েকটি key পাঠালেও বাকি defaults বজায় থাকে
- `reviews` table-এর Realtime subscription; product detail খোলা থাকলে review list refresh হয়
- Customer support chat-এর foreground incoming-message notification channel; নতুন incoming message এলে notification, tap করলে chat খোলে
- Android 13+ এ chat notification permission চাওয়ার ব্যবস্থা
- Logged-in customer-এর `is_blocked` flag refresh-এ পরীক্ষা করে blocked account sign-out
- GitHub Actions workflow এখন `main`/`master` branch-এ push, pull request এবং manual dispatch-এ Debug APK build চালায়
- App version: 1.2.0 (versionCode 4)

## PWA / Browser-only ফিচার

এগুলো Native Android app-এ সরাসরি প্রয়োজন হয় না:

- Service Worker এবং browser cache lifecycle
- Web App Manifest
- `beforeinstallprompt` / browser install banner
- Browser SEO / Open Graph metadata
- DOM-specific hover, browser history ও web-only animation behavior

Native app-এ এদের প্রয়োজনীয় user-facing কাজ Android UI, native navigation, app icon/manifest ও native caching/runtime দিয়ে পরিচালিত হয়; browser-only implementation যোগ করা হয়নি.

## এখনো বাহ্যিক সেটআপ বা যাচাই প্রয়োজন

- **FCM background/terminated push notification:** এই ZIP-এ Firebase project config, FCM sender বা server-side push sender নেই। তাই অ্যাপ background/বন্ধ থাকলে chat push notification পাঠানো সম্ভব নয়। বর্তমান notification কেবল অ্যাপ foreground-এ Realtime event পেলে কাজ করে।
- Supabase dashboard-এ `reviews`, `chats`, `chat_messages` এবং অন্যান্য প্রয়োজনীয় table-এর Realtime publication চালু আছে কি না যাচাই
- Supabase RLS policies এবং RPC permissions বাস্তব project-এ যাচাই
- Admin app থেকে settings/toggles/coupons/flash-sale/gallery পরিবর্তন করে end-to-end test
- বাস্তব Supabase data দিয়ে checkout, coupon, wallet, delivery, payment এবং order RPC edge cases পরীক্ষা
- Android SDK/Gradle দিয়ে সম্পূর্ণ build, emulator ও বাস্তব ফোনে UI/functional testing

## Build অবস্থা ও সীমাবদ্ধতা

এই কাজের execution environment-এ Android SDK/Gradle installation পাওয়া যায়নি, তাই এখানে APK compile করা সম্ভব হয়নি। XML files parse করে যাচাই করা হয়েছে; Kotlin source-এ syntax-level parser error পাওয়া যায়নি, কিন্তু এটি Android compile test-এর বিকল্প নয়। GitHub Actions workflow push-এর পর Debug APK build করার জন্য প্রস্তুত করা হয়েছে। Build fail হলে Actions log দেখে পরবর্তী সংশোধন করতে হবে.

**গুরুত্বপূর্ণ:** কোডে feature implementation থাকা আর Supabase/বাস্তব Android device-এ end-to-end verified হওয়া এক বিষয় নয়। উপরের বাহ্যিক যাচাইগুলো সম্পন্ন না হওয়া পর্যন্ত app-কে production-ready বলা হচ্ছে না।
