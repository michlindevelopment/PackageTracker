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
        Item(Kind.NEW, "SMS matching is back. Delivery messages show up on each parcel again — set it up in Settings → SMS matching."),
        Item(Kind.NEW, "It now works through a tiny separate add-on that AliTrack installs for you, so AliTrack itself never needs permission to read your messages."),
        Item(Kind.IMPROVEMENT, "Updates install without leaving the home screen — tap Update and it downloads and installs right there.")
    )
}
