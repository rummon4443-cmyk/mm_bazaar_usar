# HTML বনাম Android Native Feature Audit

রেফারেন্স: `reference-html/index.html` (ব্যবহারকারীর দেওয়া HTML)।

## Native project-এ থাকা/আংশিকভাবে থাকা
- Supabase REST/Auth সংযোগ এবং একই Supabase project ব্যবহার
- Products, categories, settings, feature toggles লোড
- Realtime refresh
- Product listing, search, category filter, price/stock filter, sorting
- Product detail, size/color selection ও variant stock check
- Cart, quantity পরিবর্তন, local cart persistence
- Wishlist
- Checkout, coupon validation, delivery zone, tax, COD/bKash/Nagad, wallet preview, WhatsApp checkout
- Order placement, order list, guest order tracking by phone, pending order cancel
- Sign in, sign up, password reset, profile edit
- Customer support chat এবং product reviews
- Banner carousel (basic), banner link, offer cards, flash sale listing
- Recently viewed products (basic), product share, image zoom
- Admin-controlled settings/toggles: brand, old price, size, color, banners, flash sale, guest checkout, hide out-of-stock, notice, WhatsApp checkout, wishlist
- Native dark mode preference control

## এই ধাপে Native-এ যুক্ত করা হয়েছে (Build/Device test বাকি)
- Recent Search: সর্বশেষ ৬টি সার্চ স্থানীয়ভাবে সংরক্ষণ, ফোকাসে চিপ দেখানো, চিপ থেকে সার্চ এবং সব হিস্ট্রি মুছুন
- Category Page: ফুল-স্ক্রিন Native ক্যাটালগ ভিউ, ক্যাটাগরি Dropdown এবং নির্বাচিত ক্যাটাগরির পণ্য তালিকা
- Product Gallery: `image_url`, `image_gallery` এবং `imageGallery` থেকে সব আলাদা ছবি, থাম্বনেইল নির্বাচন, বড় ছবি এবং সোয়াইপ করে পরের/আগের ছবি
- Cart Coupon Panel: Supabase `coupons` টেবিল থেকে সক্রিয় কুপন যাচাই, মেয়াদ, usage limit, minimum order, max discount, প্রয়োগ ও সরানো
- Cart Discount Badge এবং Admin-এর `free_delivery_threshold`, `del_in`, `del_out`, `tax` অনুযায়ী হিসাব
- Free Delivery Progress Bar
- Cart item-এর swipe-left delete এবং আলাদা quantity +/- ও delete controls
- Flash Sale-এর live countdown; নিকটতম ভবিষ্যৎ `flash_ends_at` পর্যন্ত সময় দেখানো
- Recently Viewed: সর্বশেষ ১০টি product ID রাখা, হোমে সর্বশেষ ৬টি ছবিসহ card দেখানো
- Scroll-to-Top control
- Product deep link (`?p=PRODUCT_ID`) এবং page deep link (`?go=home|category|products|cart|wishlist|profile` বা `?page=...`), Android intent filter-সহ

## এখনো অসম্পূর্ণ বা যাচাই বাকি
- এই ধাপে যোগ করা ফিচারগুলোর Android SDK/Gradle build, emulator ও বাস্তব Android ডিভাইসে functional test
- Admin app থেকে প্রতিটি coupon/flash sale/category/image-gallery setting পরিবর্তন করে end-to-end পরীক্ষা
- Profile-এর সব guest/user state ও policy/social/hotline UI parity
- Customer chat-এর unread badge/notification parity এবং realtime message behavior পূর্ণ যাচাই
- Product reviews-এর HTML-সমতুল্য সম্পূর্ণ display/reply states
- Admin app-এর প্রতিটি global_config field ও প্রতিটি feature toggle বাস্তব ডেটা দিয়ে end-to-end যাচাই
- Checkout/order totals ও সব payment/coupon edge case বাস্তব Supabase-এ end-to-end পরীক্ষা
- UI layout, responsive behavior, accessibility ও Android device testing
- Profile-এর সব guest/user state ও policy/social/hotline UI parity
- Customer chat-এর unread badge/notification parity এবং realtime message behavior পূর্ণ যাচাই
- Product reviews-এর HTML-সমতুল্য সম্পূর্ণ display/reply states
- Admin app-এর প্রতিটি global_config field ও প্রতিটি feature toggle বাস্তব ডেটা দিয়ে end-to-end যাচাই
- Checkout/order totals ও সব payment/coupon edge case বাস্তব Supabase-এ end-to-end পরীক্ষা
- UI layout, responsive behavior, accessibility ও Android device testing

## PWA-নির্দিষ্ট HTML অংশ
Service worker, browser install prompt এবং web manifest Android Native app-এ সরাসরি প্রয়োজনীয় নয়; এগুলোকে Native feature হিসেবে অনুলিপি করা হয়নি।

**সতর্কতা:** এই তালিকার বাকি কাজগুলো শেষ ও বাস্তব ডিভাইস/ব্যাকএন্ডে যাচাই না হওয়া পর্যন্ত অ্যাপটিকে সম্পূর্ণ বলা যাবে না।


## পরবর্তী সংশোধন: Admin Settings ও Chat (Static source update)

- `global_config` এবং `feature_toggles` partial JSON merge: reference defaults বজায় থাকে।
- Maintenance Mode: add-to-cart, checkout, order submit এবং WhatsApp checkout block।
- Payment toggles: সব payment বন্ধ থাকলে checkout-এ কোনো fallback COD method যোগ হয় না; bKash/Nagad number blank থাকলে option দেখানো হয় না।
- Wishlist toggle off হলে bottom-nav hide এবং direct wishlist access block।
- Header chat shortcut ও `unread_for_user` badge.
- Chat open করলে `unread_for_user=false` update.
- Foreground unread change notification channel, permission request এবং tap-to-open-chat intent.
- Realtime subscriptions-এ `chats`, `chat_messages`, `reviews`; Phoenix heartbeat এবং exponential reconnect.
- Chat timestamp UTC-তে format.

এই আপডেটের পরও FCM background push, বাস্তব Supabase RLS/Publication, Admin থেকে toggle flip করে live verification, checkout RPC totals এবং device test বাকি। এই ZIP-এর নতুন সংস্করণ Android SDK/Gradle দিয়ে build করে যাচাই করা হয়নি।
