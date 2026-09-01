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
        Item(Kind.IMPROVEMENT, "The SMS tab is temporarily gone. Android now blocks installing any app that reads your messages unless it comes from the Play Store, so the app couldn't be installed at all with it."),
        Item(Kind.IMPROVEMENT, "A replacement is coming: you'll be able to share a delivery message into the app and it'll attach to the right parcel."),
        Item(Kind.IMPROVEMENT, "Updates now install without leaving the home screen — tap Update and it downloads and installs right there.")
    )
}
