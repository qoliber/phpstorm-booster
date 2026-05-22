package com.qoliber.booster

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JPanel

class IndexBlockerConfigurable : Configurable {

    private val listModel = DefaultListModel<String>()
    private val entriesList = JBList(listModel)
    private val enabledCheckbox = JBCheckBox("Enable Index Blocker")
    private var rootPanel: JPanel? = null

    override fun getDisplayName(): String = "Index Blocker"

    override fun createComponent(): JComponent {
        val help = JBLabel(
            "<html>Folders listed below will be excluded from indexing in every project.<br/>" +
                "Supported: exact name (<code>var</code>), prefix wildcard (<code>Test*</code>), " +
                "relative path (<code>pub/static</code>).</html>"
        )

        val decorated = ToolbarDecorator.createDecorator(entriesList)
            .setAddAction { promptAdd() }
            .setRemoveAction { removeSelected() }
            .setEditAction { editSelected() }
            .disableUpDownActions()
            .createPanel()

        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(10)
            add(enabledCheckbox, BorderLayout.NORTH)
            add(decorated, BorderLayout.CENTER)
            add(help, BorderLayout.SOUTH)
        }
        rootPanel = panel
        reset()
        return panel
    }

    override fun isModified(): Boolean {
        val s = IndexBlockerSettings.getInstance()
        if (enabledCheckbox.isSelected != s.enabled) return true
        return currentEntries() != s.blockedEntries
    }

    override fun apply() {
        val s = IndexBlockerSettings.getInstance()
        s.enabled = enabledCheckbox.isSelected
        s.blockedEntries = currentEntries()
        IndexBlockerRefresher.refreshAllOpenProjects()
    }

    override fun reset() {
        val s = IndexBlockerSettings.getInstance()
        enabledCheckbox.isSelected = s.enabled
        listModel.clear()
        for (e in s.blockedEntries) listModel.addElement(e)
    }

    override fun disposeUIResources() {
        rootPanel = null
    }

    private fun currentEntries(): List<String> {
        val out = LinkedHashSet<String>()
        for (i in 0 until listModel.size()) {
            val raw = listModel.get(i).trim()
            if (raw.isNotEmpty()) out.add(raw)
        }
        return out.toList()
    }

    private fun promptAdd() {
        val parent = rootPanel
        val input = if (parent != null) {
            Messages.showInputDialog(
                parent,
                "Folder name, prefix (Test*), or relative path (pub/static):",
                "Add Blocked Folder",
                null
            )
        } else {
            Messages.showInputDialog(
                null as com.intellij.openapi.project.Project?,
                "Folder name, prefix (Test*), or relative path (pub/static):",
                "Add Blocked Folder",
                null
            )
        }?.trim().orEmpty()
        if (input.isEmpty()) return
        if (PatternMatcher.parse(input) == null) {
            Messages.showErrorDialog(parent, "Invalid entry: '$input'.", "Add Blocked Folder")
            return
        }
        if (!listModel.contains(input)) listModel.addElement(input)
    }

    private fun removeSelected() {
        val idx = entriesList.selectedIndex
        if (idx >= 0) listModel.remove(idx)
    }

    private fun editSelected() {
        val idx = entriesList.selectedIndex
        if (idx < 0) return
        val current = listModel.get(idx)
        val parent = rootPanel
        val input = if (parent != null) {
            Messages.showInputDialog(
                parent,
                "Edit entry:",
                "Edit Blocked Folder",
                null,
                current,
                null
            )
        } else {
            Messages.showInputDialog(
                null as com.intellij.openapi.project.Project?,
                "Edit entry:",
                "Edit Blocked Folder",
                null,
                current,
                null
            )
        }?.trim().orEmpty()
        if (input.isEmpty()) return
        if (PatternMatcher.parse(input) == null) {
            Messages.showErrorDialog(parent, "Invalid entry: '$input'.", "Edit Blocked Folder")
            return
        }
        listModel.set(idx, input)
    }
}
