package com.qoliber.booster

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(name = "IndexBlocker", storages = [Storage("indexBlocker.xml")])
class IndexBlockerSettings : PersistentStateComponent<IndexBlockerSettings.State> {

    class State {
        @JvmField var enabled: Boolean = true
        @JvmField var blockedEntries: MutableList<String> = mutableListOf(
            "node_modules",
            "var",
            "generated",
            "dev",
            "pub/static",
            "Test*"
        )
        @JvmField var magentoFallbackEnabled: Boolean = true
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(loaded: State) {
        XmlSerializerUtil.copyBean(loaded, state)
    }

    var enabled: Boolean
        get() = state.enabled
        set(value) { state.enabled = value }

    var blockedEntries: List<String>
        get() = state.blockedEntries.toList()
        set(value) { state.blockedEntries = value.toMutableList() }

    var magentoFallbackEnabled: Boolean
        get() = state.magentoFallbackEnabled
        set(value) { state.magentoFallbackEnabled = value }

    companion object {
        @JvmStatic
        fun getInstance(): IndexBlockerSettings =
            ApplicationManager.getApplication().getService(IndexBlockerSettings::class.java)
    }
}
