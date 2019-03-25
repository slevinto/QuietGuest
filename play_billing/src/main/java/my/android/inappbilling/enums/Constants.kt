package my.android.inappbilling.enums

import com.android.billingclient.api.BillingClient

enum class BillingOK {
    QUERY_INAPP,
    QUERY_SUBSCRIPTIONS,
    QUERY_BOTH,
    QUERY_PURCHASES,
    CONSUME_INAPP_PURCHASE
}

enum class ProductCategory(val value: String) {
    IS_CONSUMABLE("Is consumable"),
    ONE_TIME_PURCHASE("One Time Purchase - Non Consumable"),
    INAPP(BillingClient.SkuType.INAPP),
    SUBS(BillingClient.SkuType.SUBS)
}