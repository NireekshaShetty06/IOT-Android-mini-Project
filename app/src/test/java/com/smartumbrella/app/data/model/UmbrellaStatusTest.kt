package com.smartumbrella.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UmbrellaStatusTest {

    @Test
    fun defaultStatus_isOfflineAndStale() {
        val status = UmbrellaStatus()
        assertFalse("Default deviceOnline must be false", status.deviceOnline)
        assertEquals("Default lastUpdated must be 0L", 0L, status.lastUpdated)
        assertTrue("Default lastUpdated of 0L must be considered stale", status.isDataStale())
        assertFalse("Default status must not be effectively online", status.isEffectivelyOnline())
    }

    @Test
    fun recentStatus_withOnlineFlag_isEffectivelyOnline() {
        val now = System.currentTimeMillis()
        val status = UmbrellaStatus(
            deviceOnline = true,
            lastUpdated = now
        )
        assertFalse("Fresh status must not be stale", status.isDataStale(15_000L))
        assertTrue("Online flag with fresh timestamp must be effectively online", status.isEffectivelyOnline(15_000L))
    }

    @Test
    fun staleStatus_withOnlineFlag_isNotOnline() {
        val oldTime = System.currentTimeMillis() - 20_000L // 20s ago (exceeds 15s timeout)
        val status = UmbrellaStatus(
            deviceOnline = true,
            lastUpdated = oldTime
        )
        assertTrue("Status older than timeout must be stale", status.isDataStale(15_000L))
        assertFalse("Stale status must not be effectively online even if deviceOnline is true", status.isEffectivelyOnline(15_000L))
    }

    @Test
    fun freshStatus_withOfflineFlag_isNotOnline() {
        val now = System.currentTimeMillis()
        val status = UmbrellaStatus(
            deviceOnline = false,
            lastUpdated = now
        )
        assertFalse("Timestamp is fresh", status.isDataStale(15_000L))
        assertFalse("Device reporting offline must not be effectively online", status.isEffectivelyOnline(15_000L))
    }
}
