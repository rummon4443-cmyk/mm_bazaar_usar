# MM BAZAAR Admin → Native User App Settings Parity

এই আপডেটটি Admin Android project-এর `GlobalSettings`, `FeatureToggles`, `AdminRepository.saveSettings()` এবং `AdminRepository.saveToggles()`-এর সঙ্গে মিলিয়ে করা হয়েছে। Admin app `settings` টেবিলে `id=global_config` এবং `id=feature_toggles` row upsert করে; Native User App একই row ID ও JSON key পড়ে।

## Admin Feature Toggles

| Admin key | Native User App-এ প্রভাব |
|---|---|
| `f_p_brand` | Product card এবং product details-এ brand দেখানো/লুকানো |
| `f_p_size` | Product details-এ size selector দেখানো/লুকানো |
| `f_p_color` | Product details-এ color selector দেখানো/লুকানো |
| `f_p_oldprice` | Product card-এ old price/discounted comparison দেখানো/লুকানো |
| `f_s_flash_sale` | Flash Sale section এবং তার product list বন্ধ/চালু |
| `f_s_wishlist` | Wishlist bottom-nav ও product card-এর heart action বন্ধ/চালু; বন্ধ হলে Wishlist page-এ ঢোকা যায় না |
| `f_s_hide_out_stock` | Stock শেষ পণ্য product list/category list থেকে বাদ |
| `f_s_banners` | Banner carousel/frame বন্ধ/চালু |
| `f_s_marquee_notice` | Marquee notice বন্ধ/চালু |
| `f_s_guest_checkout` | Guest checkout বন্ধ হলে authentication চাইবে |
| `f_s_wa_checkout` | Checkout-এর WhatsApp order action বন্ধ/চালু |

## Admin Global Settings

`global_config` row merge করা হয় reference defaults-এর সঙ্গে। ফলে Admin-এর partial JSON payload এলে default values হারায় না এবং remote-এর অতিরিক্ত fields-ও অক্ষত থাকে।

| Admin key | Native User App-এ প্রভাব |
|---|---|
| `store_name` | Store/banner fallback title ও maintenance title |
| `marquee` | Notice text |
| `phone` | Hotline call এবং WhatsApp fallback number |
| `whatsapp` | WhatsApp checkout destination |
| `bkash`, `nagad` | Checkout-এ payment number দেখানো |
| `bkash_on`, `nagad_on`, `cod_on` | Checkout-এ payment method দেখানো/লুকানো; সব বন্ধ থাকলে checkout আটকে যায় |
| `del_in`, `del_out` | Inside/outside delivery charge |
| `tax` | Checkout tax calculation |
| `min_order` | Minimum order validation |
| `free_delivery_threshold` | Free delivery calculation |
| `maintenance` | Storefront block করার maintenance dialog; checkout/add-to-cart gate |
| `offer1_*`, `offer2_*`, `offer3_*` | Home offer cards-এর icon/title/subtitle |
| `policy_return`, `policy_privacy`, `policy_about` | Profile-এর policy/about dialogs |
| `auto_welcome_enabled`, `auto_welcome_message` | Admin app-এর chat auto-welcome workflow-এর fields; User App এগুলো overwrite করে না |
| `loyalty_rate`, `referral_bonus`, `sms_notify`, `abandoned_cart`, `otp_on`, `sms_api`, `courier_api`, `seo_title`, `seo_desc`, `pwa_name` | Admin model-এ থাকা server/admin-side metadata; Native User App এগুলো নিজের থেকে পরিবর্তন বা নতুন আচরণ অনুমান করে না |

## Live synchronization

- `StoreRealtime`-এ `settings` table subscription রয়েছে। Settings row পরিবর্তিত হলে User App আবার settings, categories, banners ও products load করে UI প্রয়োগ করে।
- Realtime connection না থাকলে foreground-এ ৬০ সেকেন্ড অন্তর fallback refresh রয়েছে।
- Product, category, banner এবং feature toggle-এর বর্তমান Supabase schema/table ব্যবহার করা হয়েছে; নতুন settings table তৈরি করা হয়নি।

## Validation performed

- Admin project-এর `GlobalSettings` ও `FeatureToggles` model-এর keys মিলিয়ে দেখা হয়েছে।
- Admin repository-তে `global_config` এবং `feature_toggles` row ID/upsert flow যাচাই করা হয়েছে।
- Native source-এ সব ১১টি feature toggle-এর consumer path যোগ/পরীক্ষা করা হয়েছে।
- Android XML ও Manifest parse validation সফল।
- Kotlin source parser-এ syntax/`expecting` error পাওয়া যায়নি; standalone `kotlinc` Android/AndroidX dependencies না থাকায় পূর্ণ compile করতে পারেনি।
- এই runtime-এ Android SDK/Gradle build environment নেই; তাই APK build এবং live Supabase/device testing এখনো হয়নি।
