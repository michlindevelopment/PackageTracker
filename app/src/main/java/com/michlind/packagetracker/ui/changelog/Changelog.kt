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
        Item(Kind.IMPROVEMENT, "Much faster AliExpress sync — tracking numbers load in parallel."),
        Item(Kind.IMPROVEMENT, "Refresh menu simplified."),
        Item(Kind.BUGFIX, "Shipping status no longer jumps backward at customs."),
        Item(Kind.NEW, "New \"In Flight\" and \"Arriving\" stages."),
        Item(Kind.NEW, "Reconnect prompt when your AliExpress session expires.")
    )
}
