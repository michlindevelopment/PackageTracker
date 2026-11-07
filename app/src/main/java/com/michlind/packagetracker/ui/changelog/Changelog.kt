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
        Item(Kind.NEW, "Statistics rebuilt: monthly spending, this month against last, average and biggest orders, delivery time (fastest, typical, slowest), top stores, and how long open orders have waited."),
        Item(Kind.NEW, "Open in AliExpress: a link to the item's page on AliExpress, at the top of a package's screen and on each order of a combined package."),
        Item(Kind.NEW, "Prices: each order's total is read from AliExpress on sync and shown on the package screen. You can also type a price when adding or editing a package."),
        Item(Kind.NEW, "Opening a combined package now shows every order in it, with photos, full names, prices and a total, on a new Orders tab. It used to open only the first one. Tapping one item inside the card still opens that item on its own."),
        Item(Kind.IMPROVEMENT, "The small info icon is gone. Names use two lines in the list and show in full on the package screen."),
        Item(Kind.NEW, "Orders with several items now show all their pictures and an item count, in the list, in search and on the detail screen."),
        Item(Kind.IMPROVEMENT, "Received now always puts the package you received most recently at the top."),
        Item(Kind.IMPROVEMENT, "If syncing with AliExpress fails, the app now tells you instead of doing nothing."),
        Item(Kind.BUGFIX, "When your AliExpress sign-in expires there is now always a way back in: Reconnect in the Refresh menu, or Connect in Settings."),
        Item(Kind.BUGFIX, "The Cainiao \"verify you're not a bot\" step prepares itself before you slide, and the blocked refresh retries on its own when you're done. Refreshing from a package's detail screen offers Verify too."),
        Item(Kind.BUGFIX, "Photos you add to a package are now kept permanently instead of being lost when Android clears the app's cache, and picking from the gallery no longer asks for storage permission."),
        Item(Kind.BUGFIX, "Average delivery time in Statistics works again. AliExpress order dates weren't being read, so no package counted. They fill in on your next sync."),
        Item(Kind.BUGFIX, "The app no longer freezes after you long-press a package and close the menu without choosing anything. Tapping outside or going back now closes the delete and mark-received confirmations too."),
        Item(Kind.BUGFIX, "Tapping quickly no longer opens the same screen several times.")
    )
}
