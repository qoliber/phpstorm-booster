package com.qoliber.booster.cachehealth

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.Registry

object SharedIndexDisabler {

    private val LOG = Logger.getInstance(SharedIndexDisabler::class.java)

    private val SHARED_INDEX_KEYS = listOf(
        "shared.indexes.download",
        "shared.indexes.php.download",
    )

    /** Force downloadable shared indexes off when enforcement is enabled. Idempotent. */
    fun enforce() {
        if (!CacheHealthSettings.getInstance().enforceDisableSharedIndexes) return
        for (key in SHARED_INDEX_KEYS) {
            try {
                val value = Registry.get(key)
                if (value.asBoolean()) {
                    value.setValue(false)
                    LOG.info("Disabled downloadable shared indexes via registry key '$key'")
                }
            } catch (t: Throwable) {
                // Unknown key in this build, or registry not ready — skip this key, keep going.
                LOG.warn("Could not set registry key '$key' (may not exist in this build)", t)
            }
        }
    }
}
