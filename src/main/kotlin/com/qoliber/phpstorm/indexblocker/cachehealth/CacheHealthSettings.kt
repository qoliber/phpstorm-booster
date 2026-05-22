package com.qoliber.phpstorm.indexblocker.cachehealth

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(name = "CacheHealth", storages = [Storage("cacheHealth.xml")])
class CacheHealthSettings : PersistentStateComponent<CacheHealthSettings.State> {

    class State {
        @JvmField var enforceDisableSharedIndexes: Boolean = true
        @JvmField var monitorEnabled: Boolean = true
        @JvmField var thresholdGb: Int = 8
        @JvmField var trimLog: Boolean = true
        @JvmField var trimJcef: Boolean = true
        @JvmField var trimFullLine: Boolean = false
    }

    private var state = State()
    override fun getState(): State = state
    override fun loadState(loaded: State) { XmlSerializerUtil.copyBean(loaded, state) }

    var enforceDisableSharedIndexes: Boolean
        get() = state.enforceDisableSharedIndexes
        set(v) { state.enforceDisableSharedIndexes = v }
    var monitorEnabled: Boolean
        get() = state.monitorEnabled
        set(v) { state.monitorEnabled = v }
    var thresholdGb: Int
        get() = state.thresholdGb
        set(v) { state.thresholdGb = v }
    var trimLog: Boolean
        get() = state.trimLog
        set(v) { state.trimLog = v }
    var trimJcef: Boolean
        get() = state.trimJcef
        set(v) { state.trimJcef = v }
    var trimFullLine: Boolean
        get() = state.trimFullLine
        set(v) { state.trimFullLine = v }

    fun thresholdBytes(): Long = thresholdGb.toLong() * 1024 * 1024 * 1024

    companion object {
        @JvmStatic
        fun getInstance(): CacheHealthSettings =
            ApplicationManager.getApplication().getService(CacheHealthSettings::class.java)
    }
}
