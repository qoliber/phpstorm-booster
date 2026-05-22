package com.qoliber.booster.cachehealth

import com.intellij.ide.AppLifecycleListener

class CacheHealthStartup : AppLifecycleListener {
    override fun appStarted() {
        SharedIndexDisabler.enforce()
        CacheSizeMonitor.check()
    }
}
