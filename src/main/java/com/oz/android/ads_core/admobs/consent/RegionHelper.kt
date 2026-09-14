package com.oz.android.ads_core.admobs.consent

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale
import java.util.TimeZone

/**
 * Helper to determine whether a user is in an EU/EEA/UK/CH region subject to GDPR.
 *
 * Only uses **physical location signals** — NOT user preference signals (locale/language),
 * since a Vietnamese user who set their phone language to German should not be treated as EU.
 *
 * Signals checked (in order of reliability):
 * 1. SIM Card Country ISO — set by the carrier at registration, most reliable
 * 2. Cellular Network Country ISO — the currently connected tower country
 * 3. Device TimeZone — best proxy for tablets/WiFi-only devices with no SIM
 *
 * If ANY signal conclusively confirms EU or non-EU, it short-circuits.
 * SIM is authoritative — if SIM says non-EU, result is non-EU regardless of timezone.
 *
 * Fail-safe: Any exception returns true (require GDPR consent) for compliance safety.
 */
object RegionHelper {

    // 27 EU member states + 3 EEA countries (IS, LI, NO) + UK + Switzerland + EU overseas territories
    private val GDPR_COUNTRY_CODES = setOf(
        "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR",
        "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL",
        "PL", "PT", "RO", "SK", "SI", "ES", "SE",
        "IS", "LI", "NO",              // EEA non-EU
        "GB", "UK",                    // United Kingdom
        "CH",                          // Switzerland
        "GF", "GP", "MQ", "RE", "YT"  // French overseas territories (EU law applies)
    )

    // European timezones — used only for WiFi-only/no-SIM devices (tablets, emulators)
    private val EUROPE_TZ_PREFIXES = listOf(
        "Europe/",
        "Atlantic/Reykjavik",   // Iceland (EEA)
        "Atlantic/Azores",      // Portugal
        "Atlantic/Madeira",     // Portugal
        "Atlantic/Canary",      // Spain (Canary Islands)
        "Atlantic/Faroe",       // Faroe Islands (EU policy applies)
        "Indian/Mayotte",       // Mayotte (French territory, EU law)
        "Indian/Reunion"        // Réunion (French territory, EU law)
    )

    /**
     * Returns true if the device is in an EU/EEA/UK/CH region requiring GDPR consent.
     * Returns false for clearly non-EU devices, allowing immediate ad initialization.
     */
    fun isEuRegion(context: Context): Boolean {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val simIso = tm?.simCountryIso?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotEmpty() }
            val networkIso = tm?.networkCountryIso?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotEmpty() }
            val timezone = TimeZone.getDefault().id
            classifyRegion(simIso, networkIso, timezone)
        } catch (t: Throwable) {
            true // Fail safe: require consent if we cannot determine location
        }
    }

    /**
     * Pure classification logic, separated from Android API calls for testability.
     *
     * @param simIso      SIM country ISO (uppercase), null if no SIM
     * @param networkIso  Network country ISO (uppercase), null if unavailable
     * @param timezone    Device timezone ID (e.g. "Europe/Berlin")
     */
    internal fun classifyRegion(simIso: String?, networkIso: String?, timezone: String): Boolean {
        // 1. SIM is the most reliable signal — it reflects the user's carrier country
        if (simIso != null) return GDPR_COUNTRY_CODES.contains(simIso)

        // 2. Network country — tower the device is currently connected to
        if (networkIso != null) return GDPR_COUNTRY_CODES.contains(networkIso)

        // 3. Timezone — last resort for tablets/WiFi-only with no SIM inserted
        return EUROPE_TZ_PREFIXES.any { prefix -> timezone.startsWith(prefix, ignoreCase = true) }
    }
}
