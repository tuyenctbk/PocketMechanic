package com.example.donation

enum class DonationTier(
    val productId: String,
    val title: String,
    val defaultPriceLabel: String,
    val badgeLabel: String
) {
    TIER_1(
        productId = "pocket_tip_tier_1",
        title = "Coffee Boost",
        defaultPriceLabel = "$1.99",
        badgeLabel = "Tier 1"
    ),
    TIER_2(
        productId = "pocket_tip_tier_2",
        title = "Shop Wrench",
        defaultPriceLabel = "$4.99",
        badgeLabel = "Tier 2"
    ),
    TIER_3(
        productId = "pocket_tip_tier_3",
        title = "Diagnostic Pro",
        defaultPriceLabel = "$9.99",
        badgeLabel = "Tier 3"
    ),
    TIER_4(
        productId = "pocket_tip_tier_4",
        title = "Master Mechanic",
        defaultPriceLabel = "$19.99",
        badgeLabel = "Tier 4"
    )
}

enum class PayPalPreset(
    val amountUsd: Int?,
    val label: String
) {
    THREE(3, "$3"),
    FIVE(5, "$5"),
    TEN(10, "$10"),
    ANY(null, "Any Amount")
}

object DonationConfig {
    // Update these handles cleanly when publishing under your creator profile
    const val PAYPAL_USERNAME = "pocketmechanic"
    const val KOFI_USERNAME = "pocketmechanic"

    fun buildPayPalUrl(amountUsd: Int?): String {
        val base = "https://paypal.me/$PAYPAL_USERNAME"
        return if (amountUsd != null && amountUsd > 0) {
            "$base/${amountUsd}USD"
        } else {
            base
        }
    }

    fun buildKofiUrl(): String = "https://ko-fi.com/$KOFI_USERNAME"

    fun buildPlayStoreUrl(packageName: String): String =
        "https://play.google.com/store/apps/details?id=$packageName"

    fun buildShareAppText(packageName: String): String =
        "Check out PocketMechanic — it diagnoses weird engine noises, error codes, and leaks using AI + Google-grounded repair manuals: ${buildPlayStoreUrl(packageName)}"
}
