# পরিবর্তনের তালিকা — MM BAZAAR Android 1.2.0

## HTML Feature Parity ও Native behavior
- Native product review list-এ Admin reply দেখানো এবং খোলা product detail-এ Realtime review update
- Supabase Realtime-এ reviews subscription, Phoenix heartbeat ও exponential reconnect backoff
- Customer support-এর foreground incoming message notification এবং notification tap থেকে chat খোলা
- Android 13+ notification permission request
- Admin settings / feature toggles-এর partial JSON merge defaults
- Blocked customer account refresh-এর সময় sign out
- Checkout-এ disabled/ফাঁকা bKash বা Nagad নম্বরের payment option বাদ; order submit-এ payment toggle পুনরায় যাচাই
- Guest checkout toggle, maintenance mode ও WhatsApp checkout toggle submit-level validation
- GitHub Actions build workflow push, pull request ও manual run-এ চালু

## গুরুত্বপূর্ণ সীমাবদ্ধতা
- FCM background/terminated push-এর জন্য Firebase config এবং server-side sender প্রয়োজন; এই ZIP-এ নেই।
- Android SDK/Gradle build environment না থাকায় এই archive-এ APK compile test চালানো যায়নি। GitHub Actions build ফলাফল যাচাই করতে হবে।
- Supabase RLS/Realtime publication এবং বাস্তব checkout/order flow device ও backend-এ পরীক্ষা করতে হবে।
