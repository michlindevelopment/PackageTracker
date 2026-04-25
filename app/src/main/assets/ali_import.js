(function () {
  'use strict';

  var BRIDGE = window.AliBridge;
  if (!BRIDGE) return;

  // Network spy — capture any XHR / fetch URL whose path looks tracking-related,
  // so if the popover loads its data via a separate API we can call it directly.
  (function () {
    if (window.__aliNetSpyInstalled) return;
    window.__aliNetSpyInstalled = true;
    var TRACKING_HINT = /track|logistic|mailno|waybill|delivery|order\.detail/i;
    try {
      var origOpen = XMLHttpRequest.prototype.open;
      XMLHttpRequest.prototype.open = function (method, url) {
        try { this.__aliUrl = url; } catch (_) {}
        return origOpen.apply(this, arguments);
      };
      var origSend = XMLHttpRequest.prototype.send;
      XMLHttpRequest.prototype.send = function () {
        var u = this.__aliUrl || '';
        if (u && TRACKING_HINT.test(u)) {
          console.log('[Ali] XHR ' + (u + '').slice(0, 300));
          var self = this;
          this.addEventListener('load', function () {
            try {
              var rt = (self.responseText || '').slice(0, 1500);
              console.log('[Ali] XHR resp(1.5k) ' + rt);
            } catch (_) {}
          });
        }
        return origSend.apply(this, arguments);
      };
    } catch (_) {}
    try {
      if (window.fetch) {
        var origFetch = window.fetch;
        window.fetch = function (input, init) {
          var url = (typeof input === 'string') ? input : (input && input.url) || '';
          if (url && TRACKING_HINT.test(url)) {
            console.log('[Ali] fetch ' + (url + '').slice(0, 300));
          }
          return origFetch.apply(this, arguments);
        };
      }
    } catch (_) {}
  })();

  // Load configuration with fallbacks so the script still runs if
  // ali_import_config.js wasn't loaded for some reason.
  var DEFAULTS = {
    maxExpandPasses: 5,
    listPageSize: 20,
    listInterPageDelayMs: 200,
    forceDetailFetch: true,
    detailFetchDelayMs: 200,
    noDetailDelayMs: 50,
    expandClickDelayMs: 1200,
    expandEmptyPassDelayMs: 800,
    expandEmptyPassesNeeded: 2,
    mtopWaitIntervalMs: 200,
    mtopWaitMaxTries: 50,
    domTrackingEnabled: true,
    iframeHardTimeoutMs: 12000,
    iframePollIntervalMs: 250,
    iframePollMaxTries: 40,
    domHoverPollIntervalMs: 100,
    domHoverPollMaxTries: 30,
    domHoverPostDelayMs: 150,
    domScrapeTimeoutMs: 4000,
    domListingEnabled: true
  };
  var CFG = (function () {
    var src = window.__AliImportConfig || {};
    var out = {};
    for (var k in DEFAULTS) out[k] = (k in src) ? src[k] : DEFAULTS[k];
    return out;
  })();
  console.log('[Ali] config: ' + JSON.stringify(CFG));

  function mtop(api, data) {
    return new Promise(function (resolve, reject) {
      if (!window.lib || !window.lib.mtop) {
        reject(new Error('mtop library not loaded'));
        return;
      }
      window.lib.mtop.request(
        {
          api: api,
          v: '1.0',
          needLogin: true,
          timeout: 15000,
          dataType: 'originaljsonp',
          data: data
        },
        resolve,
        function (err) { reject(err); }
      );
    });
  }

  function sleep(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }

  // Click any visible "view orders" / "view more" / "load more" button until stable.
  // AliExpress collapses long order lists behind these — without expanding them
  // the listing API still misses them in the first page span.
  var EXPAND_PATTERNS = [
    /view\s+(\d+\s+)?more\s*(orders)?/i,
    /view\s+all\s+orders?/i,
    /load\s+more/i,
    /show\s+more/i,
    /see\s+more/i,
    /more\s+orders/i
  ];

  function isVisible(el) {
    if (!el || !el.getBoundingClientRect) return false;
    var r = el.getBoundingClientRect();
    if (r.width === 0 || r.height === 0) return false;
    if (el.offsetParent === null) return false;
    var s = window.getComputedStyle(el);
    if (s.visibility === 'hidden' || s.display === 'none') return false;
    return true;
  }

  // Hard-coded structural selectors for the AliExpress orders page —
  // [data-pl="order_more"] wraps the "View more" button at the bottom of the list.
  var STRUCTURAL_SELECTORS = [
    '[data-pl="order_more"] button',
    '.order-more button'
  ];

  function findExpanders() {
    var hits = [];
    var seen = new Set();

    // Layer 1: structural selector — most reliable, language-independent.
    for (var s = 0; s < STRUCTURAL_SELECTORS.length; s++) {
      var nodes = document.querySelectorAll(STRUCTURAL_SELECTORS[s]);
      for (var n = 0; n < nodes.length; n++) {
        var el = nodes[n];
        if (seen.has(el) || !isVisible(el) || el.disabled) continue;
        seen.add(el);
        var label = (el.innerText || el.textContent || '').trim() || '<unlabeled>';
        hits.push({ el: el, text: label, source: STRUCTURAL_SELECTORS[s] });
      }
    }

    // Layer 2: text-pattern fallback for anything the selector missed
    // (e.g. older AliExpress layouts).
    var candidates = document.querySelectorAll(
      'button, a, [role="button"], .btn, [class*="more"], [class*="View"]'
    );
    for (var i = 0; i < candidates.length; i++) {
      var c = candidates[i];
      if (seen.has(c) || !isVisible(c)) continue;
      var text = (c.innerText || c.textContent || '').trim();
      if (!text || text.length > 60) continue;
      for (var j = 0; j < EXPAND_PATTERNS.length; j++) {
        if (EXPAND_PATTERNS[j].test(text)) {
          seen.add(c);
          hits.push({ el: c, text: text, source: 'text' });
          break;
        }
      }
    }
    return hits;
  }

  // React/Vue components sometimes ignore plain .click() — dispatch a real
  // MouseEvent that bubbles + uses the element's center so the framework's
  // synthetic event handlers wire up.
  function realClick(el) {
    var rect = el.getBoundingClientRect();
    var x = rect.left + rect.width / 2;
    var y = rect.top + rect.height / 2;
    var opts = { bubbles: true, cancelable: true, clientX: x, clientY: y, view: window };
    el.dispatchEvent(new MouseEvent('mousedown', opts));
    el.dispatchEvent(new MouseEvent('mouseup', opts));
    el.dispatchEvent(new MouseEvent('click', opts));
  }

  function expandAllOrders() {
    return new Promise(function (resolve) {
      var emptyPasses = 0;
      var pass = 0;
      // Counts only passes where we actually clicked a button — that way an
      // early "still rendering" empty pass doesn't burn the budget.
      var clickPasses = 0;
      function step() {
        pass++;
        var hits = findExpanders();

        // Scroll-trigger any IntersectionObserver-based lazy loaders.
        try { window.scrollTo(0, document.body.scrollHeight); } catch (_) {}

        if (hits.length === 0) {
          emptyPasses++;
          if (emptyPasses >= CFG.expandEmptyPassesNeeded) {
            console.log('[Ali] expandAllOrders done pass=' + pass +
              ' clickPasses=' + clickPasses + ' (no more expanders)');
            resolve();
            return;
          }
          setTimeout(step, CFG.expandEmptyPassDelayMs);
          return;
        }
        emptyPasses = 0;

        if (clickPasses >= CFG.maxExpandPasses) {
          console.log('[Ali] expandAllOrders cap reached clickPasses=' + clickPasses +
            ' (maxExpandPasses=' + CFG.maxExpandPasses + ')');
          resolve();
          return;
        }
        clickPasses++;

        console.log('[Ali] expand pass ' + clickPasses + '/' + CFG.maxExpandPasses +
          ': clicking ' + hits.length + ' [' +
          hits.map(function (h) { return '"' + h.text + '" (' + h.source + ')'; }).join(', ') + ']');
        for (var i = 0; i < hits.length; i++) {
          try {
            hits[i].el.scrollIntoView({ block: 'center' });
            realClick(hits[i].el);
          } catch (e) {
            console.log('[Ali] click failed: ' + (e && e.message));
          }
        }
        setTimeout(step, CFG.expandClickDelayMs);
      }
      step();
    });
  }

  function getCookie(name, key) {
    var cookies = document.cookie.split(';');
    for (var i = 0; i < cookies.length; i++) {
      var c = cookies[i].trim();
      if (c.indexOf(name + '=') === 0) {
        var v = c.substring(name.length + 1);
        if (key) {
          var m = v.match(new RegExp('(?:^|&)' + key + '=([^&]+)'));
          return m ? m[1] : '';
        }
        return v;
      }
    }
    return '';
  }

  function timeZoneTag() {
    var m = new Date().toString().match(/([A-Z]+[+-][0-9]+)/);
    return m ? m[1] : '';
  }

  function baseCtx() {
    var country = getCookie('aep_usuc_f', 'region') || 'US';
    return {
      shipToCountry: country === 'CN' ? 'US' : country,
      lang: getCookie('aep_usuc_f', 'b_locale') || 'en_US'
    };
  }

  function fetchList(pageIndex, pageSize) {
    var ctx = baseCtx();
    return mtop('mtop.aliexpress.trade.buyer.order.list', {
      statusTab: 'all',
      renderType: 'init',
      clientPlatform: 'pc',
      shipToCountry: ctx.shipToCountry,
      _lang: ctx.lang,
      timeZone: timeZoneTag(),
      pageIndex: pageIndex,
      pageSize: pageSize
    });
  }

  function fetchDetail(orderId) {
    var ctx = baseCtx();
    return mtop('mtop.aliexpress.trade.buyer.order.detail', {
      orderId: String(orderId),
      clientPlatform: 'pc',
      shipToCountry: ctx.shipToCountry,
      _lang: ctx.lang
    });
  }

  function componentsOf(res) {
    return (res && res.data && res.data.data) || {};
  }

  function extractOrders(res) {
    var bag = componentsOf(res);
    var orders = [];
    var tagCounts = {};
    Object.keys(bag).forEach(function (k) {
      var item = bag[k];
      if (!item || !item.tag) return;
      tagCounts[item.tag] = (tagCounts[item.tag] || 0) + 1;
      if (item.tag === 'pc_om_list_order' && item.fields) {
        var f = item.fields;
        var line = (f.orderLines && f.orderLines[0]) || {};
        orders.push({
          orderId: String(f.orderId),
          name: line.itemTitle || '',
          imageUrl: line.itemImgUrl || '',
          orderStatus: (f.utParams && f.utParams.args && f.utParams.args.orderStatus) || 0,
          orderDateText: f.orderDateText || ''
        });
      }
    });
    console.log('[Ali] list page tags=' + JSON.stringify(tagCounts) + ' orders=' + orders.length);
    return orders;
  }

  function extractHasMore(res) {
    var bag = componentsOf(res);
    var keys = Object.keys(bag);
    for (var i = 0; i < keys.length; i++) {
      var item = bag[keys[i]];
      if (item && item.tag === 'pc_om_list_body' && item.fields) {
        return !!item.fields.hasMore;
      }
    }
    return false;
  }

  // Recursively walk an object collecting any string value whose key looks like a
  // tracking-number field. Used as a fallback when the legacy
  // `detail_logistic_package_block` tag is missing or restructured.
  function deepFindTrackingNumber(obj, depth) {
    if (!obj || depth > 8) return null;
    if (typeof obj !== 'object') return null;
    var keys = Object.keys(obj);
    for (var i = 0; i < keys.length; i++) {
      var k = keys[i];
      var v = obj[k];
      if (typeof v === 'string' && v.length > 4 && v.length < 80) {
        var lk = k.toLowerCase();
        if (lk === 'trackingnumber' || lk === 'tracking_no' || lk === 'mailno' ||
            lk === 'logisticsno' || lk === 'logistics_number' || lk === 'waybillno') {
          return v;
        }
      } else if (v && typeof v === 'object') {
        var found = deepFindTrackingNumber(v, depth + 1);
        if (found) return found;
      }
    }
    return null;
  }

  function extractTrackingNumber(res, orderId) {
    var bag = componentsOf(res);
    var keys = Object.keys(bag);
    var tagsSeen = [];
    var found = null;
    for (var i = 0; i < keys.length; i++) {
      var item = bag[keys[i]];
      if (!item || !item.tag) continue;
      tagsSeen.push(item.tag);
      if (item.tag === 'detail_logistic_package_block' && item.fields) {
        var pkgs = item.fields.packageInfoList || [];
        if (pkgs.length && pkgs[0].trackingNumber) found = String(pkgs[0].trackingNumber);
      }
    }
    // Fallback: walk the full response looking for any tracking-shaped field.
    if (!found) found = deepFindTrackingNumber(res, 0);

    // First time we see a detail response with no tracking, dump the FULL top-level
    // structure once so we can see where AliExpress moved the data.
    if (!found && !window.__aliDetailDumped) {
      window.__aliDetailDumped = true;
      try {
        console.log('[Ali] FIRST FAILED DETAIL — order=' + orderId +
          ' topKeys=' + JSON.stringify(Object.keys(res || {})) +
          ' dataKeys=' + JSON.stringify(Object.keys((res && res.data) || {})) +
          ' bagKeys=' + JSON.stringify(Object.keys(bag || {})) +
          ' tagsSeen=' + JSON.stringify(tagsSeen));
        // Truncated raw dump (first 3kb) to capture nested structure.
        var raw = JSON.stringify(res);
        if (raw && raw.length > 3000) raw = raw.slice(0, 3000) + '…[truncated]';
        console.log('[Ali] raw detail (first 3kb): ' + raw);
      } catch (e) {
        console.log('[Ali] dump failed: ' + (e && e.message));
      }
    }

    console.log('[Ali] detail order=' + orderId +
      ' tn=' + (found || '<null>') +
      ' tagsSeen=' + JSON.stringify(tagsSeen));
    return found;
  }

  function parseOrderDate(text) {
    var t = new Date(text).getTime();
    return isNaN(t) ? Date.now() : t;
  }

  // After expansion finishes, dump enough state to figure out where the order
  // cards live in the DOM (or whether they live in the DOM at all).
  function dumpPostExpandDomState() {
    var bodyText = (document.body && document.body.innerText) || '';
    console.log('[Ali] DOM dump: body.innerText length=' + bodyText.length);

    // Count occurrences of likely identifiers / phrases.
    var idMatches = bodyText.match(/\b\d{15,19}\b/g) || [];
    var orderIdLabel = (bodyText.match(/Order ID/gi) || []).length;
    console.log('[Ali] DOM dump: orderId-shaped numbers in text=' + idMatches.length +
      ' (first 5: ' + JSON.stringify(idMatches.slice(0, 5)) + ')' +
      ' "Order ID" labels=' + orderIdLabel);

    // Likely structural containers.
    var selectors = [
      '.order-wrap', '.order-main', '.order-content', '.order-list',
      '[class*="order-item"]', '[class*="orderItem"]', '[class*="order_item"]',
      '[data-spm="order_list_main"]', '[data-pl="order_more"]',
      '[class*="order"]'
    ];
    var counts = {};
    for (var i = 0; i < selectors.length; i++) {
      try { counts[selectors[i]] = document.querySelectorAll(selectors[i]).length; }
      catch (_) { counts[selectors[i]] = 'err'; }
    }
    console.log('[Ali] DOM dump: structural counts=' + JSON.stringify(counts));

    // Any element carrying an order-id-shaped data attribute?
    var dataAttrHits = [];
    var allEls = document.querySelectorAll('*');
    for (var j = 0; j < allEls.length && dataAttrHits.length < 8; j++) {
      var el = allEls[j];
      if (!el.attributes) continue;
      for (var a = 0; a < el.attributes.length; a++) {
        var attr = el.attributes[a];
        if (attr.name.indexOf('data-') !== 0) continue;
        if (/^\d{15,19}$/.test(attr.value)) {
          dataAttrHits.push({ tag: el.tagName, attr: attr.name, val: attr.value });
          break;
        }
      }
    }
    console.log('[Ali] DOM dump: data-attr orderId hits=' + JSON.stringify(dataAttrHits));

    // Sample of body text — first 2 KB of innerText.
    console.log('[Ali] DOM dump: body.innerText (first 2kb)=' +
      bodyText.slice(0, 2000).replace(/\s+/g, ' '));

    // First .order-content element (if any) — dump its outerHTML up to 4 KB.
    var oc = document.querySelector('.order-content') ||
             document.querySelector('[data-spm="order_list_main"]') ||
             document.querySelector('.order-main');
    if (oc) {
      var html = (oc.outerHTML || '').slice(0, 4000);
      console.log('[Ali] DOM dump: order-container outerHTML (first 4kb)=' + html);
    } else {
      console.log('[Ali] DOM dump: no order-content/order-main element found');
    }
  }

  // Each AliExpress order card is `<div class="order-item">`. Inside it:
  //   * `.order-item-header-right-info` carries "Order ID: <digits>" + "Order date: ..."
  //   * the Track button is an `<a class="...order-item-btn">` whose href has
  //     `tradeOrderId=<digits>` — that's the most reliable id source.
  //   * product title lives in `.order-item-content-info-name span[title]`
  //   * product image is a CSS `background-image` on `.order-item-content-img`.
  function extractOrdersFromDOM() {
    var cards = document.querySelectorAll('.order-item');
    var orders = [];
    var seen = Object.create(null);

    for (var i = 0; i < cards.length; i++) {
      var card = cards[i];

      // 1) Order ID — prefer the track link's tradeOrderId param.
      var orderId = null;
      var trackLink = card.querySelector('a[href*="tradeOrderId="]');
      if (trackLink) {
        var hm = (trackLink.getAttribute('href') || '').match(/tradeOrderId=(\d{10,20})/);
        if (hm) orderId = hm[1];
      }
      if (!orderId) {
        var headerInfo = card.querySelector('.order-item-header-right-info');
        var headerText = headerInfo ? (headerInfo.innerText || headerInfo.textContent || '') : '';
        var im = headerText.match(/(\d{15,19})/);
        if (im) orderId = im[1];
      }
      if (!orderId || seen[orderId]) continue;
      seen[orderId] = true;

      // 2) Product title — `<span title="...">` is the canonical full title.
      var name = '';
      var titleSpan = card.querySelector('.order-item-content-info-name span[title]');
      if (titleSpan) {
        name = titleSpan.getAttribute('title') ||
               titleSpan.innerText || titleSpan.textContent || '';
      }
      if (!name) {
        var anyTitle = card.querySelector('.order-item-content-info-name');
        if (anyTitle) name = (anyTitle.innerText || anyTitle.textContent || '').trim();
      }

      // 3) Image — extract URL from the inline background-image style.
      var imageUrl = '';
      var imgDiv = card.querySelector('.order-item-content-img');
      if (imgDiv) {
        var bg = (imgDiv.style && imgDiv.style.backgroundImage) || '';
        var um = bg.match(/url\(["']?([^"')]+)["']?\)/);
        if (um) imageUrl = um[1];
      }
      if (!imageUrl) {
        var imgEl = card.querySelector('img');
        if (imgEl) imageUrl = imgEl.getAttribute('src') || imgEl.src || '';
      }

      // 4) Order date — plain text "Order date: <text>" inside the header.
      var dateText = '';
      var headerInfoEl = card.querySelector('.order-item-header-right-info');
      var headerInfoTxt = headerInfoEl
        ? (headerInfoEl.innerText || headerInfoEl.textContent || '')
        : (card.innerText || card.textContent || '');
      var dm = headerInfoTxt.match(/Order date[:\s]+([^\n]+)/i);
      if (dm) dateText = dm[1].trim();

      orders.push({
        orderId: orderId,
        name: (name || '').trim(),
        imageUrl: imageUrl,
        orderStatus: 0,
        orderDateText: dateText
      });
    }
    console.log('[Ali] DOM listing extracted ' + orders.length + ' orders ' +
      '(.order-item nodes=' + cards.length + ')');
    return orders;
  }

  // Hover-fire so AliExpress's popover library opens the tracking-info tooltip.
  // Comet UI listens for PointerEvent (modern path) and falls back to MouseEvent.
  // We send the full sequence so React's synthetic-event handlers definitely catch it.
  function dispatchHover(el) {
    var rect = el.getBoundingClientRect();
    var x = rect.left + rect.width / 2;
    var y = rect.top + rect.height / 2;
    var pointerOpts = {
      bubbles: true, cancelable: true, composed: true,
      clientX: x, clientY: y, view: window,
      pointerType: 'mouse', isPrimary: true
    };
    var mouseOpts = {
      bubbles: true, cancelable: true, composed: true,
      clientX: x, clientY: y, view: window
    };
    try { el.dispatchEvent(new PointerEvent('pointerover', pointerOpts)); } catch (_) {}
    try { el.dispatchEvent(new PointerEvent('pointerenter', pointerOpts)); } catch (_) {}
    el.dispatchEvent(new MouseEvent('mouseover', mouseOpts));
    el.dispatchEvent(new MouseEvent('mouseenter', mouseOpts));
    el.dispatchEvent(new MouseEvent('mousemove', mouseOpts));
    try { el.dispatchEvent(new PointerEvent('pointermove', pointerOpts)); } catch (_) {}
  }

  function dispatchUnhover(el) {
    var rect = el.getBoundingClientRect();
    var x = rect.left + rect.width / 2;
    var y = rect.top + rect.height / 2;
    var pointerOpts = {
      bubbles: true, cancelable: true, composed: true,
      clientX: x, clientY: y, view: window,
      pointerType: 'mouse', isPrimary: true
    };
    var mouseOpts = {
      bubbles: true, cancelable: true, composed: true,
      clientX: x, clientY: y, view: window
    };
    try { el.dispatchEvent(new PointerEvent('pointerout', pointerOpts)); } catch (_) {}
    try { el.dispatchEvent(new PointerEvent('pointerleave', pointerOpts)); } catch (_) {}
    el.dispatchEvent(new MouseEvent('mouseout', mouseOpts));
    el.dispatchEvent(new MouseEvent('mouseleave', mouseOpts));
  }

  // One-shot diagnostic: snapshot popover-related elements before & after the
  // first hover so we can see whether the synthetic events triggered anything.
  function dumpPopoverState(label, orderId) {
    if (window.__aliPopoverDumped && label === 'after') return;
    var counts = {
      '.tracking-number-title': document.querySelectorAll('.tracking-number-title').length,
      '.comet-popover': document.querySelectorAll('.comet-popover').length,
      '.comet-popover-body': document.querySelectorAll('.comet-popover-body').length,
      '.comet-popover-wrap': document.querySelectorAll('.comet-popover-wrap').length,
      '.order-track-popover': document.querySelectorAll('.order-track-popover').length,
      '.order-track-popup': document.querySelectorAll('.order-track-popup').length
    };
    console.log('[Ali] popover ' + label + ' order=' + orderId + ' counts=' + JSON.stringify(counts));
    if (label === 'after') {
      var pop = document.querySelector('.comet-popover-body, .comet-popover, .order-track-popover');
      if (pop) {
        var html = (pop.outerHTML || '').slice(0, 3000);
        console.log('[Ali] popover after HTML(3k): ' + html);
      }
      window.__aliPopoverDumped = true;
    }
  }

  // The Track button gets identified by structural cues (class containing
  // "track", any data-pl with "track", parent with "order-track" class) AND
  // by text matching common languages — AliExpress localizes the label.
  var TRACK_TEXT_PATTERNS = [
    /track\s*order/i,        // English
    /track/i,                // English short
    /מעקב/,                 // Hebrew
    /отследить/i,            // Russian
    /rastrear/i,             // Portuguese / Spanish
    /seguir/i,               // Spanish
    /suivre/i,               // French
    /verfolgen/i,            // German
    /traccia/i,              // Italian
    /追踪/                   // Chinese
  ];

  function looksLikeTrackButton(btn) {
    var t = (btn.innerText || btn.textContent || '').trim();
    if (t && t.length <= 40) {
      for (var i = 0; i < TRACK_TEXT_PATTERNS.length; i++) {
        if (TRACK_TEXT_PATTERNS[i].test(t)) return true;
      }
    }
    var cls = (btn.className || '') + '';
    if (/track/i.test(cls)) return true;
    var dpl = btn.getAttribute && btn.getAttribute('data-pl');
    if (dpl && /track/i.test(dpl)) return true;
    // Check immediate parent — sometimes the button itself is unlabeled and
    // the wrapper carries the data-pl/class.
    var p = btn.parentElement;
    if (p) {
      if (/order[-_]?track/i.test((p.className || '') + '')) return true;
      var pdpl = p.getAttribute && p.getAttribute('data-pl');
      if (pdpl && /track/i.test(pdpl)) return true;
    }
    return false;
  }

  // The Track button is `<a class="...order-item-btn" href="...tradeOrderId=ID...">`.
  // Find by href, then walk up to the `.order-item` ancestor (the whole card).
  function findOrderCardAndTrackButton(orderId) {
    var sel = 'a[href*="tradeOrderId=' + orderId + '"]';
    var link = document.querySelector(sel);
    if (!link) return null;
    var card = link;
    var hops = 0;
    while (card && hops < 12) {
      if (card.classList && card.classList.contains('order-item')) break;
      card = card.parentElement;
      hops++;
    }
    return { card: card || link, button: link };
  }

  // One-shot diagnostic dump — when the very first order's DOM scrape fails to
  // find a track button, log the HTML around its order ID so we can see the
  // actual structure.
  function dumpFirstFailedCard(orderId) {
    if (window.__aliCardDumped) return;
    window.__aliCardDumped = true;
    try {
      // Search for any element whose text contains the orderId.
      var nodes = document.querySelectorAll('div, p, span, td');
      var hit = null;
      for (var i = 0; i < nodes.length; i++) {
        var t = nodes[i].innerText || nodes[i].textContent || '';
        if (t.length > 0 && t.length < 4000 && t.indexOf(orderId) !== -1) {
          hit = nodes[i];
          break;
        }
      }
      if (!hit) {
        console.log('[Ali] DUMP: order ' + orderId +
          ' text NOT found anywhere in the DOM');
        return;
      }
      var anc = hit;
      for (var j = 0; j < 6 && anc.parentElement; j++) anc = anc.parentElement;
      var html = (anc.outerHTML || '').slice(0, 4000);
      var btnSummary = [];
      var btns = anc.querySelectorAll('button, [role="button"], a');
      for (var k = 0; k < btns.length && k < 12; k++) {
        var bt = btns[k];
        btnSummary.push({
          text: ((bt.innerText || bt.textContent || '').trim() || '').slice(0, 40),
          cls: ((bt.className || '') + '').slice(0, 80),
          dpl: bt.getAttribute && bt.getAttribute('data-pl'),
          parentCls: (bt.parentElement && bt.parentElement.className || '').slice(0, 80)
        });
      }
      console.log('[Ali] DUMP card HTML (first 4kb): ' + html);
      console.log('[Ali] DUMP buttons in card: ' + JSON.stringify(btnSummary));
    } catch (e) {
      console.log('[Ali] DUMP failed: ' + (e && e.message));
    }
  }

  // Read tracking number from the popover that appears after hovering "Track order".
  // The popover renders a `.tracking-number-title` <p> whose <span> child is
  // the tracking number. There can be at most one open popover at a time, so
  // we just look for the first matching node in the document.
  function readTrackingFromPopover() {
    var titles = document.querySelectorAll('.tracking-number-title');
    for (var i = 0; i < titles.length; i++) {
      var spans = titles[i].querySelectorAll('span');
      for (var j = 0; j < spans.length; j++) {
        var s = (spans[j].innerText || spans[j].textContent || '').trim();
        if (s && s.length >= 6 && s.length <= 50) return s;
      }
    }
    return null;
  }

  // Load /p/tracking/index.html?tradeOrderId=ID into a hidden same-origin
  // iframe and wait for the tracking number element to render. The element's
  // class has a build hash (e.g. logistic-info-v2--mailNoValue--X0fPzen) so we
  // match by substring `[class*="mailNoValue"]`.
  function getTrackingViaIframe(orderId) {
    return new Promise(function (resolve) {
      var IFRAME_ID = '__aliTrackerFrame';
      var iframe = document.getElementById(IFRAME_ID);
      if (!iframe) {
        iframe = document.createElement('iframe');
        iframe.id = IFRAME_ID;
        iframe.setAttribute('aria-hidden', 'true');
        iframe.style.cssText = 'position:absolute;width:1px;height:1px;border:0;left:-99999px;top:-99999px;';
        document.body.appendChild(iframe);
      }

      var resolved = false;
      var pollTimer = null;
      var hardTimer = setTimeout(function () {
        if (resolved) return;
        resolved = true;
        if (pollTimer) clearTimeout(pollTimer);
        console.log('[Ali] iframe TIMEOUT order=' + orderId);
        resolve(null);
      }, CFG.iframeHardTimeoutMs);

      function tryRead(label) {
        if (resolved) return false;
        try {
          var doc = iframe.contentDocument ||
            (iframe.contentWindow && iframe.contentWindow.document);
          if (!doc) return false;
          var el = doc.querySelector('[class*="mailNoValue"]');
          if (el) {
            var tn = (el.innerText || el.textContent || '').trim();
            if (tn && tn.length >= 4 && tn.length <= 60) {
              resolved = true;
              clearTimeout(hardTimer);
              if (pollTimer) clearTimeout(pollTimer);
              console.log('[Ali] iframe order=' + orderId + ' tn=' + tn + ' via=' + label);
              resolve(tn);
              return true;
            }
          }
        } catch (e) {
          console.log('[Ali] iframe read err order=' + orderId + ': ' + (e && e.message));
        }
        return false;
      }

      iframe.onload = function () {
        if (tryRead('onload')) return;
        var tries = 0;
        (function poll() {
          if (resolved) return;
          tries++;
          if (tryRead('poll-' + tries)) return;
          if (tries >= CFG.iframePollMaxTries) {
            if (!resolved) {
              resolved = true;
              clearTimeout(hardTimer);
              console.log('[Ali] iframe order=' + orderId + ' tn=<null> tries=' + tries);
              resolve(null);
            }
            return;
          }
          pollTimer = setTimeout(poll, CFG.iframePollIntervalMs);
        })();
      };

      var url = '/p/tracking/index.html?tradeOrderId=' + encodeURIComponent(orderId) +
        '&_addShare=no&_login=yes';
      try { iframe.src = url; }
      catch (e) {
        if (!resolved) {
          resolved = true;
          clearTimeout(hardTimer);
          console.log('[Ali] iframe src failed order=' + orderId + ': ' + (e && e.message));
          resolve(null);
        }
      }
    });
  }

  function getTrackingFromDOM(orderId) {
    var inner = new Promise(function (resolve) {
      var found;
      try { found = findOrderCardAndTrackButton(orderId); } catch (e) {
        console.log('[Ali] DOM scrape findCard threw: ' + (e && e.message));
        resolve(null);
        return;
      }
      if (!found) {
        console.log('[Ali] DOM scrape: no card/track-button found for order=' + orderId);
        dumpFirstFailedCard(orderId);
        resolve(null);
        return;
      }
      try { found.button.scrollIntoView({ block: 'center' }); } catch (_) {}
      setTimeout(function () {
        try { dumpPopoverState('before', orderId); } catch (_) {}
        try { dispatchHover(found.button); } catch (_) {}
        var tries = 0;
        function poll() {
          tries++;
          var tn = null;
          try { tn = readTrackingFromPopover(); } catch (_) {}
          if (tn || tries >= CFG.domHoverPollMaxTries) {
            try { dumpPopoverState('after', orderId); } catch (_) {}
            try { dispatchUnhover(found.button); } catch (_) {}
            try {
              document.body.dispatchEvent(new MouseEvent('mousedown',
                { bubbles: true, clientX: 1, clientY: 1, view: window }));
            } catch (_) {}
            console.log('[Ali] DOM scrape order=' + orderId + ' tn=' +
              (tn || '<null>') + ' tries=' + tries);
            resolve(tn);
            return;
          }
          setTimeout(poll, CFG.domHoverPollIntervalMs);
        }
        setTimeout(poll, CFG.domHoverPollIntervalMs);
      }, CFG.domHoverPostDelayMs);
    });

    // Hard timeout — guarantees the chain advances even if the popover never
    // renders or `poll` somehow stalls.
    var timeout = new Promise(function (resolve) {
      setTimeout(function () {
        console.log('[Ali] DOM scrape TIMEOUT order=' + orderId);
        resolve(null);
      }, CFG.domScrapeTimeoutMs);
    });

    return Promise.race([inner, timeout]);
  }

  function importAll() {
    var page = 1;
    var all = [];

    var seenIds = Object.create(null);

    function fetchNextPage() {
      try { BRIDGE.onProgress('Fetching page ' + page + '…'); } catch (_) {}
      return fetchList(page, CFG.listPageSize).then(function (res) {
        var orders = extractOrders(res);
        var added = 0;
        for (var i = 0; i < orders.length; i++) {
          var id = orders[i].orderId;
          if (seenIds[id]) continue;
          seenIds[id] = true;
          all.push(orders[i]);
          added++;
        }
        var hasMore = extractHasMore(res);
        console.log('[Ali] page ' + page + ': fetched=' + orders.length +
          ' new=' + added + ' total=' + all.length + ' hasMore=' + hasMore);
        page++;
        // Stop if the API claims no more, OR we got a page with no new orders
        // (server is cycling).
        if (!hasMore || added === 0) return finishListing();
        return sleep(CFG.listInterPageDelayMs).then(fetchNextPage);
      });
    }

    // Interleave extract → click → wait → extract. This handles both
    // append-style and replace-style "View orders" behaviors.
    function expandAndExtract() {
      return new Promise(function (resolve) {
        var clickPasses = 0;
        function ingestSnapshot() {
          var snap = extractOrdersFromDOM();
          var added = 0;
          for (var i = 0; i < snap.length; i++) {
            var id = snap[i].orderId;
            if (seenIds[id]) continue;
            seenIds[id] = true;
            all.push(snap[i]);
            added++;
          }
          console.log('[Ali] snapshot: +' + added + ' new (total=' + all.length + ')');
          return added;
        }
        function step() {
          ingestSnapshot();
          if (clickPasses >= CFG.maxExpandPasses) {
            console.log('[Ali] expand cap reached at pass=' + clickPasses);
            resolve();
            return;
          }
          // Scroll to bottom — triggers any IntersectionObserver loaders.
          try { window.scrollTo(0, document.body.scrollHeight); } catch (_) {}
          var hits = findExpanders();
          if (hits.length === 0) {
            console.log('[Ali] no expander button — listing complete pass=' + clickPasses);
            resolve();
            return;
          }
          clickPasses++;
          console.log('[Ali] expand pass ' + clickPasses + '/' + CFG.maxExpandPasses +
            ': clicking ' + hits.length + ' [' +
            hits.map(function (h) { return '"' + h.text + '" (' + h.source + ')'; }).join(', ') + ']');
          for (var k = 0; k < hits.length; k++) {
            try {
              hits[k].el.scrollIntoView({ block: 'center' });
              realClick(hits[k].el);
            } catch (e) {
              console.log('[Ali] click failed: ' + (e && e.message));
            }
          }
          setTimeout(step, CFG.expandClickDelayMs);
        }
        step();
      });
    }

    function startListing() {
      try { BRIDGE.onProgress('Expanding hidden orders…'); } catch (_) {}
      if (!CFG.domListingEnabled) return fetchNextPage();
      return expandAndExtract().then(function () {
        if (all.length > 0) {
          console.log('[Ali] using DOM listing total=' + all.length);
          return finishListing();
        }
        console.log('[Ali] DOM listing empty — running diagnostic dump');
        try { dumpPostExpandDomState(); } catch (e) {
          console.log('[Ali] dumpPostExpandDomState threw: ' + (e && e.message));
        }
        console.log('[Ali] falling back to API pagination');
        return fetchNextPage();
      });
    }

    function finishListing() {
      try { BRIDGE.onTotal(all.length); } catch (_) {}
      return processNext(0);
    }

    function processNext(i) {
      if (i >= all.length) {
        try { BRIDGE.onComplete(); } catch (_) {}
        return;
      }
      var o = all[i];
      console.log('[Ali] order ' + (i + 1) + '/' + all.length +
        ' id=' + o.orderId + ' name="' + (o.name || '').slice(0, 40) +
        '" status=' + o.orderStatus);

      // Primary path: load /p/tracking/index.html in a hidden iframe and read
      // the tracking number from `[class*="mailNoValue"]`. The hover popover
      // and the legacy detail API both no longer expose the tracking number;
      // they're kept as silent fallbacks only.
      var trackingPromise = CFG.domTrackingEnabled
        ? getTrackingViaIframe(o.orderId).then(function (tn) {
            if (tn) return tn;
            return fetchDetail(o.orderId)
              .then(function (res) { return extractTrackingNumber(res, o.orderId); })
              .catch(function () { return null; });
          })
        : Promise.resolve(null);

      return trackingPromise.catch(function (err) {
        console.log('[Ali] tracking lookup error order=' + o.orderId + ' err=' +
          (err && (err.msg || err.message) || err));
        return null;
      }).then(function (tn) {
        var payload = {
          orderId: o.orderId,
          name: o.name,
          imageUrl: o.imageUrl || null,
          trackingNumber: tn,
          orderCreatedAt: parseOrderDate(o.orderDateText)
        };
        try { BRIDGE.onOrder(JSON.stringify(payload), i + 1, all.length); } catch (_) {}
        return sleep(CFG.detailFetchDelayMs).then(function () { return processNext(i + 1); });
      });
    }

    startListing().catch(function (err) {
      var msg = (err && (err.msg || err.message)) || 'Unknown error';
      try { BRIDGE.onError(String(msg)); } catch (_) {}
    });
  }

  var tries = 0;
  var waiter = setInterval(function () {
    tries++;
    if (window.lib && window.lib.mtop) {
      clearInterval(waiter);
      importAll();
    } else if (tries > 50) {
      clearInterval(waiter);
      try { BRIDGE.onError('mtop library not ready after 10s'); } catch (_) {}
    }
  }, 200);
})();
