package com.qoliber.phpstorm.indexblocker

import com.intellij.openapi.options.Configurable
import javax.swing.JComponent
import javax.swing.JPanel

class IndexBlockerConfigurable : Configurable {
    override fun getDisplayName(): String = "Index Blocker"
    override fun createComponent(): JComponent = JPanel()
    override fun isModified(): Boolean = false
    override fun apply() {}
    override fun reset() {}
}
