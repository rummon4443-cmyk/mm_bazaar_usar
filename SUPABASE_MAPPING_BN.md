# Supabase Mapping — Native User App

| Feature | Existing Supabase source |
|---|---|
| Store products | `products` |
| Categories | `categories` |
| Banners | `banners` |
| Admin configuration | `settings.id=global_config` |
| Feature switches | `settings.id=feature_toggles` |
| Customer profile/wallet/points/block status | `users` (`wallet_balance`, `loyalty_points`, `is_blocked`, `wallet_log`) |
| Coupons | `coupons` |
| Orders | `orders` |
| Customer reviews | `reviews` |
| Chat thread/messages | `chats`, `chat_messages` |

## Existing RPC contracts used

- `place_order_atomic`: `p_customer_name`, `p_phone`, `p_address`, `p_notes`, `p_delivery_zone`, `p_delivery_charge`, `p_tax`, `p_wallet_used`, `p_payment_method`, `p_trx_id`, `p_coupon_code`, `p_items`. Each item includes `id`, `qty`, `selectedSize`, `selectedColor`.
- `cancel_order_atomic`: `p_order_id`
- `submit_review_atomic`: `p_product_id`, `p_user_name`, `p_rating`, `p_text`
- `track_orders_by_phone`: `p_phone`
- `get_guest_orders_by_ids`: `p_ids`

The app does not create or migrate database tables and does not contain a service-role key. The backend's existing RPC and RLS remain authoritative for checkout and wallet operations.
