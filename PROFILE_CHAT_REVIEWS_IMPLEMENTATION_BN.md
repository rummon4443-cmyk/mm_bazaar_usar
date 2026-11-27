# MM BAZAAR Native: Profile, Chat & Reviews update

এই আপডেটে HTML reference-এর `page-profile`, `openPolicy`, `callHelpline`, `openChat`, `sendChat`, `loadChatMessages`, `listenCustomerChatBadge`, `renderProductReviews`, এবং `submitReview` আচরণের ভিত্তিতে Native Kotlin/XML UI উন্নত করা হয়েছে। WebView যোগ করা হয়নি।

## যুক্ত / উন্নত করা হয়েছে

- Guest Profile: Login/Registration, guest order list, phone order tracking, Return Policy, Privacy Policy, About, Facebook, YouTube, Hotline.
- Signed-in Profile: Name/email/phone/address, wallet balance, loyalty points, profile edit, all orders, tracking, customer chat, policy/social/hotline links, logout.
- Header-এ Chat shortcut ও unread badge.
- `chats.unread_for_user` থেকে unread state পড়া; chat খুললে `false` করে mark-as-read.
- নতুন incoming unread state শনাক্ত হলে Android notification channel/notification; notification tap করলে app-এর chat খুলবে.
- Supabase Realtime subscription-এ signed-in user's `chats` ও `chat_messages` যুক্ত; Phoenix heartbeat যোগ করা হয়েছে.
- Chat history `chat_messages` থেকে timestamp অনুযায়ী দেখানো এবং HTML-এর মতো `chats` upsert + `chat_messages` insert flow.
- Product reviews-এ নাম, rating stars, timestamp, comment এবং Admin-এর `reply` field দেখানো.
- Review submit-এর জন্য HTML-এ ব্যবহৃত `submit_review_atomic` RPC ও parameter names বজায় রাখা.
- Policy content-এর জন্য Admin-managed `settings` row-এর `policy_return`, `policy_privacy`, `policy_about` key ব্যবহার.
- Facebook/YouTube URL ও hotline number HTML reference অনুযায়ী; hotline number Admin settings-এর `phone` থেকে, fallback `01937175424`.

## ডেটাবেস প্রত্যাশা

HTML reference-এ ব্যবহৃত একই Supabase schema/columns প্রয়োজন:
- `users`: `id`, `name`, `phone`, `address`, `wallet_balance`, `loyalty_points`, `is_blocked`
- `chats`: `id`, `user_id`, `user_name`, `phone`, `email`, `last_message`, `unread_for_admin`, `unread_for_user`, `updated_at`
- `chat_messages`: `chat_id`, `sender_id`, `text`, `timestamp`
- `reviews`: `product_id`, `user_name`, `rating`, `text`, `reply`, `created_at`
- RPC: `submit_review_atomic`
- `settings` / `global_config` data: `policy_return`, `policy_privacy`, `policy_about`, `phone`

Supabase RLS policies-তে signed-in customer-কে নিজের `chats`/`chat_messages` পড়া-লেখা এবং নিজের profile পড়া/আপডেটের অনুমতি থাকতে হবে। `reviews` read policy public/customer-readable এবং review RPC execute policy customer-authenticated হতে হবে। Admin reply Admin App থেকে `reviews.reply`-এ লেখা হবে; User App শুধু সেটি প্রদর্শন করে। Realtime-এর জন্য Supabase Database → Publications → `supabase_realtime`-এ `chats` ও `chat_messages` (এবং চাইলে `reviews`) টেবিল enabled থাকতে হবে।

## গুরুত্বপূর্ণ সীমাবদ্ধতা

- App foreground-এ থাকলে Supabase Realtime event ও periodic unread check দিয়ে badge/notification আপডেট হবে।
- App বন্ধ বা background-এ থাকলে নির্ভরযোগ্য push notification দিতে Firebase Cloud Messaging (FCM), Firebase project config, এবং server-side push sender/Edge Function প্রয়োজন। এই project-এ `google-services.json`/FCM sender configuration নেই, তাই background push-কে সম্পূর্ণ কার্যকর বলা হচ্ছে না।
- Android 13+ এ notification দেখাতে ব্যবহারকারীর `POST_NOTIFICATIONS` অনুমতি প্রয়োজন.
- এই পরিবেশে Android SDK/Gradle build tool নেই; তাই APK compile ও real Supabase/device test করা হয়নি।
