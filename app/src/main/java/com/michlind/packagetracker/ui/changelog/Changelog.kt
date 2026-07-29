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
        Item(Kind.NEW, "Side menu — search, sort and settings now live in one place."),
        Item(Kind.NEW, "Search your packages by name or tracking number."),
        Item(Kind.NEW, "Statistics — deliveries per month, average delivery time and more."),
        Item(Kind.NEW, "Tap a package photo to view it full screen (pinch to zoom)."),
        Item(Kind.IMPROVEMENT, "One \"On the Way\" tab — to-ship and in-transit packages together."),
        Item(Kind.IMPROVEMENT, "Clearer sort options that say what ends up on top."),
        Item(Kind.BUGFIX, "Import progress no longer jumps backward.")
    )
}
