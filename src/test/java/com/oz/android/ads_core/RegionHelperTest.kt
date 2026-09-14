package com.oz.android.ads_core

import com.oz.android.ads_core.admobs.consent.RegionHelper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionHelperTest {

    // ─── EU via SIM ───────────────────────────────────────────────────────────

    @Test fun simDe_isEu() = assertTrue(RegionHelper.classifyRegion("DE", null, "Asia/Ho_Chi_Minh"))
    @Test fun simFr_isEu() = assertTrue(RegionHelper.classifyRegion("FR", null, "America/New_York"))
    @Test fun simGb_isEu() = assertTrue(RegionHelper.classifyRegion("GB", null, "America/New_York"))
    @Test fun simCh_isEu() = assertTrue(RegionHelper.classifyRegion("CH", null, "America/New_York"))
    @Test fun simNo_isEu() = assertTrue(RegionHelper.classifyRegion("NO", null, "America/New_York"))

    // ─── Non-EU via SIM (SIM always authoritative — ignores non-EU timezone) ──

    @Test fun simUs_isNotEu() = assertFalse(RegionHelper.classifyRegion("US", null, "Europe/Berlin"))
    @Test fun simVn_isNotEu() = assertFalse(RegionHelper.classifyRegion("VN", null, "Asia/Ho_Chi_Minh"))
    @Test fun simJp_isNotEu() = assertFalse(RegionHelper.classifyRegion("JP", null, "Europe/Berlin"))
    @Test fun simIn_isNotEu() = assertFalse(RegionHelper.classifyRegion("IN", null, "America/New_York"))

    // ─── EU via Network (no SIM) ──────────────────────────────────────────────

    @Test fun networkIt_noSim_isEu() = assertTrue(RegionHelper.classifyRegion(null, "IT", "America/New_York"))
    @Test fun networkNl_noSim_isEu() = assertTrue(RegionHelper.classifyRegion(null, "NL", "Asia/Ho_Chi_Minh"))

    // ─── Non-EU via Network (no SIM) ─────────────────────────────────────────

    @Test fun networkBr_noSim_isNotEu() = assertFalse(RegionHelper.classifyRegion(null, "BR", "America/Sao_Paulo"))
    @Test fun networkUs_noSim_isNotEu() = assertFalse(RegionHelper.classifyRegion(null, "US", "America/New_York"))

    // ─── EU via Timezone only (no SIM, no network — tablet/WiFi-only) ─────────

    @Test fun tzBerlin_noSignals_isEu() = assertTrue(RegionHelper.classifyRegion(null, null, "Europe/Berlin"))
    @Test fun tzParis_noSignals_isEu() = assertTrue(RegionHelper.classifyRegion(null, null, "Europe/Paris"))
    @Test fun tzLondon_noSignals_isEu() = assertTrue(RegionHelper.classifyRegion(null, null, "Europe/London"))
    @Test fun tzReykjavik_noSignals_isEu() = assertTrue(RegionHelper.classifyRegion(null, null, "Atlantic/Reykjavik"))

    // ─── Non-EU via Timezone only ─────────────────────────────────────────────

    @Test fun tzHoChiMinh_noSignals_isNotEu() = assertFalse(RegionHelper.classifyRegion(null, null, "Asia/Ho_Chi_Minh"))
    @Test fun tzNyork_noSignals_isNotEu() = assertFalse(RegionHelper.classifyRegion(null, null, "America/New_York"))
    @Test fun tzTokyo_noSignals_isNotEu() = assertFalse(RegionHelper.classifyRegion(null, null, "Asia/Tokyo"))

    // ─── Fail-safe: Context exception → true ─────────────────────────────────

    @Test
    fun exceptionInContext_failsSafeToTrue() {
        val ctx = object : android.content.ContextWrapper(null) {
            override fun getSystemService(name: String): Any {
                throw RuntimeException("Simulated service error")
            }
        }
        assertTrue(RegionHelper.isEuRegion(ctx))
    }
}
