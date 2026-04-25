// Tunables for the AliExpress import flow.
// Loaded by AliImportScreen before ali_import.js so the main script can read
// from window.__AliImportConfig (with safe fallbacks).
window.__AliImportConfig = {
  // Maximum number of "view more" expansion clicks before we stop expanding
  // and start fetching pages. Each click reveals one more page of older
  // orders — we then import everything those expansions exposed (no separate
  // order cap).
  maxExpandPasses: 5,

  // Listing source. Prefer DOM (what you actually see on the page after the
  // "view more" expansion); the MTOP API ignores pageIndex and only returns
  // the first batch, so it's used only as a fallback when the DOM extractor
  // returns 0 orders.
  domListingEnabled: true,

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

  // Expander loop timing.
  expandClickDelayMs: 1200,
  expandEmptyPassDelayMs: 800,
  // Stop expanding after N consecutive passes that find no buttons.
  expandEmptyPassesNeeded: 2,

  // mtop library wait — script gives up if lib.mtop isn't ready in time.
  mtopWaitIntervalMs: 200,
  mtopWaitMaxTries: 50
};
