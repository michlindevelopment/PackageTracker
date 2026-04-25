// Tunables for the AliExpress import flow.
// Loaded by AliImportScreen before ali_import.js so the main script can read
// from window.__AliImportConfig (with safe fallbacks).
window.__AliImportConfig = {
  // Listing source. Prefer DOM (what you actually see on the page after
  // clicking through the "To ship" and "Shipped" tabs); the MTOP API ignores
  // pageIndex and only returns the first batch, so it's used only as a
  // fallback when the DOM extractor returns 0 orders.
  domListingEnabled: true,

  // After clicking a tab, wait this long for AliExpress to render the new
  // list before starting expansion / extraction.
  tabSettleMs: 1500,

  // Listing pagination (legacy API path).
  listPageSize: 20,
  listInterPageDelayMs: 200,

  // Per-order detail fetch (legacy API path — kept as fallback only).
  // forceDetailFetch=true ignores the orderStatus gate so every order is
  // queried for its tracking number — safer when AliExpress changes the enum.
  forceDetailFetch: true,
  detailFetchDelayMs: 200,
  noDetailDelayMs: 50,

  // Tracking-number extraction. The tracking number lives on the
  // /p/tracking/index.html page — we load that URL into a hidden same-origin
  // iframe per order, wait for [class*="mailNoValue"] to render, and read it.
  domTrackingEnabled: true,
  iframeHardTimeoutMs: 12000,
  iframePollIntervalMs: 250,
  iframePollMaxTries: 40,
  // Legacy hover scrape — kept off by default; enable only if iframe path fails.
  domHoverPollIntervalMs: 100,
  domHoverPollMaxTries: 30,
  domHoverPostDelayMs: 150,
  domScrapeTimeoutMs: 4000,

  // Expander loop timing — delay between successive "View more" clicks.
  expandClickDelayMs: 1200,
  // Safety cap on expansion passes. We expect "To ship" and "Shipped" tabs
  // to never need more than a handful of clicks, but cap at 20 just in case
  // AliExpress ever shows a runaway list.
  maxExpandPasses: 20,

  // mtop library wait — script gives up if lib.mtop isn't ready in time.
  mtopWaitIntervalMs: 200,
  mtopWaitMaxTries: 50
};
