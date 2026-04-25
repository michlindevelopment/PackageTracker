(function () {
  'use strict';

  var BRIDGE = window.AliBridge;
  if (!BRIDGE) return;

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
    Object.keys(bag).forEach(function (k) {
      var item = bag[k];
      if (item && item.tag === 'pc_om_list_order' && item.fields) {
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

  function extractTrackingNumber(res) {
    var bag = componentsOf(res);
    var keys = Object.keys(bag);
    for (var i = 0; i < keys.length; i++) {
      var item = bag[keys[i]];
      if (item && item.tag === 'detail_logistic_package_block' && item.fields) {
        var pkgs = item.fields.packageInfoList || [];
        if (pkgs.length && pkgs[0].trackingNumber) return String(pkgs[0].trackingNumber);
      }
    }
    return null;
  }

  function parseOrderDate(text) {
    var t = new Date(text).getTime();
    return isNaN(t) ? Date.now() : t;
  }

  function importAll() {
    var MAX_ORDERS = 50;
    var page = 1, pageSize = 20;
    var all = [];

    function fetchNextPage() {
      try { BRIDGE.onProgress('Fetching page ' + page + '…'); } catch (_) {}
      return fetchList(page, pageSize).then(function (res) {
        var orders = extractOrders(res);
        Array.prototype.push.apply(all, orders);
        var hasMore = extractHasMore(res);
        page++;
        if (!hasMore || all.length >= MAX_ORDERS) return finishListing();
        return sleep(200).then(fetchNextPage);
      });
    }

    function finishListing() {
      if (all.length > MAX_ORDERS) all = all.slice(0, MAX_ORDERS);
      try { BRIDGE.onTotal(all.length); } catch (_) {}
      return processNext(0);
    }

    function processNext(i) {
      if (i >= all.length) {
        try { BRIDGE.onComplete(); } catch (_) {}
        return;
      }
      var o = all[i];
      var needDetail = o.orderStatus >= 8;
      var detailPromise = needDetail
        ? fetchDetail(o.orderId).then(extractTrackingNumber).catch(function () { return null; })
        : Promise.resolve(null);

      return detailPromise.then(function (tn) {
        var payload = {
          orderId: o.orderId,
          name: o.name,
          imageUrl: o.imageUrl || null,
          trackingNumber: tn,
          orderCreatedAt: parseOrderDate(o.orderDateText)
        };
        try { BRIDGE.onOrder(JSON.stringify(payload), i + 1, all.length); } catch (_) {}
        return sleep(needDetail ? 200 : 50).then(function () { return processNext(i + 1); });
      });
    }

    fetchNextPage().catch(function (err) {
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
