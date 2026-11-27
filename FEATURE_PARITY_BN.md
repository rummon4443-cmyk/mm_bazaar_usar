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

## HTML-এর তুলনায় এখনো অসম্পূর্ণ বা যাচাই বাকি
- Home grid-এর আলাদা Quick Add এবং Buy Now action-এর সম্পূর্ণ HTML-সমতুল্য আচরণ
- Search-এর voice input, recent-search chips, clear/recent search management
- Category page-এর সম্পূর্ণ আলাদা navigation/layout এবং category dropdown parity
- Product image gallery thumbnails ও সব gallery image-এর zoom
- Cart-এর HTML-সমতুল্য coupon panel, discount badge, delivery progress bar, swipe-to-delete
- Flash sale-এর live countdown timer
- Recently viewed-এর সম্পূর্ণ HTML card design ও আচরণ
- Scroll-to-top control
- Deep-link handling (product/page links)
- Profile-এর সব guest/user state ও policy/social/hotline UI parity
- Customer chat-এর unread badge/notification parity এবং realtime message behavior পূর্ণ যাচাই
- Product reviews-এর HTML-সমতুল্য সম্পূর্ণ display/reply states
- Admin app-এর প্রতিটি global_config field ও প্রতিটি feature toggle বাস্তব ডেটা দিয়ে end-to-end যাচাই
- Checkout/order totals ও সব payment/coupon edge case বাস্তব Supabase-এ end-to-end পরীক্ষা
- UI layout, responsive behavior, accessibility ও Android device testing

## PWA-নির্দিষ্ট HTML অংশ
Service worker, browser install prompt এবং web manifest Android Native app-এ সরাসরি প্রয়োজনীয় নয়; এগুলোকে Native feature হিসেবে অনুলিপি করা হয়নি।

**সতর্কতা:** এই তালিকার বাকি কাজগুলো শেষ ও বাস্তব ডিভাইস/ব্যাকএন্ডে যাচাই না হওয়া পর্যন্ত অ্যাপটিকে সম্পূর্ণ বলা যাবে না।
