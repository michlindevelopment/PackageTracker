package com.michlind.packagetracker.ui.changelog

/**
 * "What's New" content. Shown once whenever the installed version changes
 * (see the version check in HomeViewModel) — no per-version keying, just edit
 * these items each release. The version number in the popup header comes from
 * the build (BuildConfig.VERSION_NAME).
 *
 * Each item has a [Kind] (rendered as a coloured badge) and a line of text.
 */
object Changelog {

    /** Change type — drives the coloured badge next to each line. */
    enum class Kind(val label: String) {
        NEW("New"),
        IMPROVEMENT("Imp"),
        BUGFIX("Fix")
    }

    data class Item(val kind: Kind, val text: String)

    val ITEMS: List<Item> = listOf(
        Item(Kind.BUGFIX, "Package status was wrong for some shipments — parcels already on their way could still show as \"Order Placed\"."),
        Item(Kind.IMPROVEMENT, "Status no longer slips backwards after customs, and failed or returned deliveries are flagged properly."),
        Item(Kind.IMPROVEMENT, "Carrier updates we don't recognise are now read for what they say instead of being ignored.")
    )
}
