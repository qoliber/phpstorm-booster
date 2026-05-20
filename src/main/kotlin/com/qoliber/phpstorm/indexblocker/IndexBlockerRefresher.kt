package com.qoliber.phpstorm.indexblocker

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class IndexBlockerRefresher(private val project: Project) {
    fun refresh() {
        // Filled in by Task 6
    }
}
