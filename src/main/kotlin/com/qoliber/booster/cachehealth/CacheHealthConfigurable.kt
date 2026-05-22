package com.qoliber.booster.cachehealth

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

class CacheHealthConfigurable : BoundConfigurable("Cache Health") {

    override fun createPanel(): DialogPanel {
        val s = CacheHealthSettings.getInstance()
        return panel {
            group("Shared Indexes") {
                row {
                    checkBox("Force downloadable shared indexes off on startup")
                        .bindSelected({ s.enforceDisableSharedIndexes }, { s.enforceDisableSharedIndexes = it })
                }
            }
            group("Cache Size Monitor") {
                row {
                    checkBox("Warn when index cache exceeds threshold")
                        .bindSelected({ s.monitorEnabled }, { s.monitorEnabled = it })
                }
                row("Threshold (GB):") {
                    intTextField(0..1024)
                        .bindIntText({ s.thresholdGb }, { s.thresholdGb = it })
                }
            }
            group("Quick Trim Targets") {
                row { checkBox("Logs").bindSelected({ s.trimLog }, { s.trimLog = it }) }
                row { checkBox("Embedded browser cache (jcef_cache)").bindSelected({ s.trimJcef }, { s.trimJcef = it }) }
                row { checkBox("Full Line AI cache").bindSelected({ s.trimFullLine }, { s.trimFullLine = it }) }
            }
        }
    }

    override fun apply() {
        super.apply()
        SharedIndexDisabler.enforce()
    }
}
