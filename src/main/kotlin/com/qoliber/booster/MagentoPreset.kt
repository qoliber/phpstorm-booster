package com.qoliber.booster

object MagentoPreset {
    /** Root-relative build/generated directories to exclude in Magento projects. */
    val dirs: List<String> = listOf(
        "generated",
        "var",
        "pub/static",
        "pub/media",
        "setup",
        "node_modules",
    )
}
