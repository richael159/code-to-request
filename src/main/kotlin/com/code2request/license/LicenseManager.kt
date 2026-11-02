package com.code2request.license

/**
 * License manager stub — reserved for future Pro-tier features.
 * First MVP version does not implement any license verification logic.
 */
object LicenseManager {

    enum class Tier { FREE, PRO }

    fun currentTier(): Tier = Tier.FREE

    fun isEnabled(feature: ProFeature): Boolean = false

    enum class ProFeature {
        CLOUD_SYNC,
        TEAM_COLLABORATION,
        REQUEST_COLLECTIONS,
        ADVANCED_AUTH
    }
}
