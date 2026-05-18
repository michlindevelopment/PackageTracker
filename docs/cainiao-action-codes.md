# Cainiao `actionCode` Reference

- **Source:** Alibaba TOP API — `aliexpress.logistics.redefining.querytrackingresult`
- **URL:** https://developer.alibaba.com/docs/api.htm?scopeId=12782&apiId=30120
- **Note:** Official enum ends with "等" (*etc*) — list is **NOT** exhaustive. Codes marked *observed* or *suspected* aren't in the published reference but have been seen in real payloads.

This is the source of truth for [`StatusMapper.kt`](../app/src/main/java/com/michlind/packagetracker/util/StatusMapper.kt). When Cainiao returns an unknown code in the wild, decide which bucket it falls into here first, then update the mapper.

---

## Dispatch / origin

| Code                   | Meaning                                              |
| ---------------------- | ---------------------------------------------------- |
| `CONSIGN`              | Shipment declared                                    |
| `OM_CONSIGN`           | Merchant dispatched                                  |
| `GTMS_ASN`             | Electronic advance notice (ASN) received             |
| `LAST_MILE_ASN_NOTIFY` | Last-mile ASN forecast *(observed in payload)*       |

## Pickup (`PU_*`)

| Code                 | Meaning                  |
| -------------------- | ------------------------ |
| `PU_PICKUP_SUCCESS`  | Pickup OK                |
| `PU_PICKUP_FAILURE`  | Pickup failed            |
| `PU_SIGN_IN_SUCCESS` | Signed at pickup point   |
| `PU_SIGN_IN_FAILURE` | Pickup sign fail         |

## Origin warehouse (`CW_*`) — *observed, not in official enum*

| Code          | Meaning            |
| ------------- | ------------------ |
| `CW_INBOUND`  | Arrived warehouse  |
| `CW_OUTBOUND` | Departed warehouse |

## Sorting center, origin (`SC_*`)

| Code                  | Meaning                       |
| --------------------- | ----------------------------- |
| `SC_INBOUND_SUCCESS`  | Inbound at sorting center     |
| `SC_INBOUND_FAILURE`  | SC inbound fail               |
| `SC_OUTBOUND_SUCCESS` | Outbound from sorting center  |
| `SC_OUTBOUND_FAILURE` | SC outbound fail              |
| `SC_HO_OUT_SUCCESS`   | SC handed to line-haul OK     |
| `SC_HO_OUT_FAILURE`   | SC handoff fail               |
| `SC_SIGN_IN_SUCCESS`  | SC sign-in OK                 |
| `SC_SIGN_IN_FAILURE`  | SC sign-in fail               |

## Transit hub (`TD_*`)

| Code              | Meaning             |
| ----------------- | ------------------- |
| `TD_TRANS_ARRIVE` | Arrived transit hub |
| `TD_TRANS_DEPART` | Left transit hub    |

## Export customs (`CC_EX_*`)

| Code            | Meaning                    |
| --------------- | -------------------------- |
| `CC_EX_START`   | Export clearance started   |
| `CC_EX_SUCCESS` | Export cleared             |
| `CC_EX_FAILURE` | Export clearance failed    |

## Line-haul / airline (`LH_*`)

| Code                    | Meaning                                |
| ----------------------- | -------------------------------------- |
| `LH_HO_IN_SUCCESS`      | Handover-in OK (arrived hub)           |
| `LH_HO_IN_FAILURE`      | Handover-in fail                       |
| `LH_POST_COLLECTION`    | Post collection handover fail          |
| `LH_HO_AIRLINE`         | Handed to airline / aviation security  |
| `LH_HO_AIRLINE_FAILURE` | Aviation security fail                 |
| `LH_DEPART`             | Flight departed origin                 |
| `LH_ARRIVE`             | Flight arrived destination country     |
| `LH_ARRIVE_FAILURE`     | Line-haul arrival fail                 |
| `LH_HO_OUT_SUCCESS`     | Line-haul handover-out OK              |
| `LH_HO_OUT_FAILURE`     | Line-haul handover-out fail            |
| `LH_OTHER`              | Other line-haul event                  |

## Transit-country clearance (`CC_TRANS_*`)

| Code               | Meaning                              |
| ------------------ | ------------------------------------ |
| `CC_TRANS_START`   | Transit-country clearance started    |
| `CC_TRANS_SUCCESS` | Transit-country clearance OK         |
| `CC_TRANS_FAILURE` | Transit-country clearance failed     |

## Import customs (`CC_IM_*`, `CC_HO_*`, `CUS_*`, `CIQ_*`)

| Code                | Meaning                              |
| ------------------- | ------------------------------------ |
| `CC_IM_START`       | Destination clearance started        |
| `CC_IM_PROCESS`     | Destination clearance in progress    |
| `CC_IM_SUCCESS`     | Destination cleared                  |
| `CC_IM_FAILURE`     | Destination clearance fail           |
| `CC_HO_IN_SUCCESS`  | Customs handover-in OK               |
| `CC_HO_IN_FAILURE`  | Customs handover-in fail             |
| `CC_HO_OUT_SUCCESS` | Customs handover-out OK              |
| `CC_HO_OUT_FAILURE` | Customs handover-out fail            |
| `CUS_DECLARE`       | Customs declaration submitted        |
| `CUS_SUCCESS`       | Customs review passed                |
| `CUS_FAILURE`       | Customs review fail                  |
| `CUS_TAX`           | **DUTIES DUE** (customs tax assessed) |
| `CIQ_SUCCESS`       | Inspection & quarantine OK           |
| `CIQ_FAILURE`       | Inspection & quarantine fail         |

## Destination delivery (`GTMS_*`)

| Code                     | Meaning                                              |
| ------------------------ | ---------------------------------------------------- |
| `GTMS_ACCEPT`            | Arrived destination country / accepted by local carrier |
| `GTMS_SC_ARRIVE`         | Arrived destination sorting center                   |
| `GTMS_SC_ARRIVE_FAILURE` | Destination SC arrival fail                          |
| `GTMS_DO_ARRIVE`         | Arrived last delivery station                        |
| `GTMS_DO_DEPART`         | Left last delivery station                           |
| `GTMS_DELIVERING`        | Out for delivery                                     |
| `GTMS_DEL_FAILURE`       | Delivery failed                                      |
| `GTMS_RE_DELIVERING`     | Re-delivery attempt                                  |
| `GTMS_RELABEL`           | Re-labeled / transferred                             |
| `GTMS_SIGNED`            | **DELIVERED** (signed by recipient)                  |
| `GTMS_SIGN_FAILURE`      | Signature fail                                       |
| `GTMS_WAIT_SELF_PICK`    | Awaiting customer self-pickup                        |
| `GTMS_STA_SIGNED`        | Signed at pickup station                             |
| `GTMS_STA_SIGN_FAILURE`  | Pickup-station sign fail                             |
| `GTMS_STATION_IN`        | Foreign delivery node (in)                           |
| `GTMS_STATION_OUT`       | Foreign delivery node (out)                          |
| `GTMS_FAILURE`           | Delivery exception                                   |
| `GTMS_OTHER`             | Other GTMS event                                     |

## Re-routing (address changes)

| Code                                | Meaning                                       |
| ----------------------------------- | --------------------------------------------- |
| `GTMS_STA_WAIT_CUST_CHANGE_ADDRESS` | Awaiting consumer to update address           |
| `GTMS_STA_CUST_TO_DOOR`             | Consumer updated recipient address            |
| `GTMS_STA_CHANGE`                   | Consumer updated pickup-point address         |
| `GTMS_STA_CN_TO_DOOR`               | System updated recipient address              |
| `GTMS_STA_CN_CHANGE`                | System updated pickup-point address           |
| `GTMS_STA_CNCP_TO_DOOR`             | Carrier updated recipient address             |
| `GTMS_STA_CNCP_CHANGE`              | Carrier updated pickup-point address          |

## Pickup point / self pickup (`GSTA_*`, `STA_*`)

| Code                   | Meaning                                  |
| ---------------------- | ---------------------------------------- |
| `GSTA_SIGN`            | User signed at pickup point              |
| `GSTA_SIGN_FAIL`       | User sign fail at pickup point           |
| `STA_SIGN`             | Pickup-point sign (domestic CN)          |
| `STA_SIGN_FAIL`        | Pickup-point sign fail (domestic CN)     |
| `GSTAHUB_INBOUND`      | Pickup distribution-center inbound OK    |
| `GSTAHUB_INBOUND_FAIL` | Pickup distribution-center inbound fail  |
| `GSTA_INBOUND`         | Pickup-point inbound OK                  |
| `GSTA_INBOUND_FAIL`    | Pickup-point inbound fail                |
| `GSTA_BUYER_SIGN`      | Buyer signed at pickup point             |
| `GSTA_BUYER_SIGN_FAIL` | Buyer sign fail at pickup point          |
| `GSTA_OTHER`           | Other pickup-point event                 |

## Domestic (China) delivery

| Code        | Meaning                       |
| ----------- | ----------------------------- |
| `GOT`       | Domestic pickup OK            |
| `REJECT`    | Domestic pickup fail          |
| `DEPARTURE` | Domestic node (depart)        |
| `ARRIVAL`   | Domestic node (arrive)        |
| `SENT_SCAN` | Out for delivery (domestic)   |
| `SIGNED`    | Delivered (domestic)          |
| `FAILED`    | Delivery fail (domestic)      |

## Global warehouse (`GWMS_*`)

| Code             | Meaning             |
| ---------------- | ------------------- |
| `GWMS_ACCEPT`    | Order accepted      |
| `GWMS_REJECT`    | Order accept fail   |
| `GWMS_PICK`      | Picking complete    |
| `GWMS_PACKAGE`   | Packed              |
| `GWMS_OUTBOUND`  | Outbound OK         |
| `GWMS_HANDOVER`  | Handover OK         |
| `GWMS_EXCEPTION` | Warehouse exception |
| `GWMS_CANCEL`    | Cancelled           |

## Returns (`RT_*`)

| Code                    | Meaning                                            |
| ----------------------- | -------------------------------------------------- |
| `RT_INBOUND`            | Returned to seller (overseas CP warehouse)         |
| `RETURNED`              | Return delivered to seller                         |
| `RT_OUTBOUND`           | Overseas CP outbound                               |
| `RT_DESTROY`            | Overseas CP destroyed                              |
| `RT_HO_OUT`             | CP handed out                                      |
| `RT_WH_HO_IN`           | Return warehouse received                          |
| `RT_WH_HO_IN_FAIL`      | Return warehouse receive fail                      |
| `RT_WH_INBOUND`         | Return warehouse inbound                           |
| `RT_WH_INBOUND_FAILURE` | Return warehouse inbound fail                      |
| `RT_WH_OUTBOUND`        | Return warehouse outbound                          |
| `RT_WH_HO_OUT`          | Return warehouse handed out                        |
| `RT_WH_DESTROY`         | Destroyed in return warehouse                      |
| `RT_TRANSWH_HO_IN`      | Transit warehouse received                         |
| `RT_TRANSWH_INBOUND`    | Transit warehouse inbound                          |
| `RT_TRANSWH_OUTBOUND`   | Transit warehouse outbound                         |
| `RT_TMS_ACCEPT`         | Domestic carrier accepted return                   |

## Locker (`cainiao.endpoint.locker.top.order.tracking`)

| Code           | Meaning                              |
| -------------- | ------------------------------------ |
| `POSTMAN_POST` | Postman deposited package in locker  |

## Suspected newer codes — *not in official enum, handled as fallback*

| Code               | Meaning                                                    |
| ------------------ | ---------------------------------------------------------- |
| `COMMON_INTRANSIT` | Generic in-transit (carrier-agnostic fallback)             |
| `COMMON_ACCEPT`    | Generic accepted (fallback)                                |
| `COMMON_DEPART`    | Generic departed (fallback)                                |
| `COMMON_SIGNED`    | Generic delivered (fallback)                               |

`COMMON_INTRANSIT` is added to `ADVISORY_ACTION_CODES` in `PackageRepositoryImpl.kt` and skipped when picking the latest meaningful event — Cainiao slaps it on pre-shipment ASNs ("Pre-Shipment Info Sent To …") which would otherwise read as real movement.

---

## Group node codes (`group.nodeCode`)

High-level buckets, useful for the timeline grouping but not for status mapping.

| Code                           | Meaning                              |
| ------------------------------ | ------------------------------------ |
| `AE_GROUP_CW_PROCESSING`       | In warehouse                         |
| `AE_GROUP_SC_PROCESSING`       | In transit (sorting)                 |
| `AE_GROUP_EX_CLEARING_CUSTOMS` | At export customs                    |
| `AE_GROUP_LH_PROCESSING`       | In transit (line-haul)               |
| `AE_GROUP_IM_CLEARING_CUSTOMS` | At import customs *(inferred)*       |
| `AE_GROUP_GWMS_PROCESSING`     | Destination warehouse *(inferred)*   |
| `AE_GROUP_GTMS_DELIVERING`     | Out for delivery *(inferred)*        |
| `AE_GROUP_SIGNED`              | Delivered *(inferred)*               |

---

## Status buckets → `PackageStatus`

Reference table for how the codes above are bucketed in [`StatusMapper.kt`](../app/src/main/java/com/michlind/packagetracker/util/StatusMapper.kt). When the action code resolves to `UNKNOWN`, the mapper falls back to `progressRate` (6 equal slices across the in-flight statuses — see `mapByProgress`).

| Bucket             | Codes                                                                                  | App status                                                |
| ------------------ | -------------------------------------------------------------------------------------- | --------------------------------------------------------- |
| **CREATED**        | `CONSIGN`, `OM_CONSIGN`, `GTMS_ASN`, `LAST_MILE_ASN_NOTIFY`, `GWMS_ACCEPT`             | `ORDER_PLACED`                                            |
| **ORIGIN_PROCESS** | `PU_*`, `CW_*`, `SC_*`, `GWMS_*` (except `ACCEPT`)                                     | `SHIPPED`                                                 |
| **EXPORT_CUSTOMS** | `CC_EX_*`                                                                              | `CUSTOMS_EXPORT`                                          |
| **IN_TRANSIT**     | `LH_*`, `TD_*`, `CC_TRANS_*`, `COMMON_INTRANSIT`, `COMMON_DEPART`                      | `IN_TRANSIT`                                              |
| **IMPORT_CUSTOMS** | `CC_IM_*`, `CC_HO_*`, `CUS_*` (except `CUS_TAX`), `CIQ_*`                              | `CUSTOMS_IMPORT`                                          |
| **DUTIES_DUE**     | `CUS_TAX`                                                                              | `CUSTOMS_IMPORT` *(no dedicated enum value yet)*          |
| **LAST_MILE**      | `GTMS_ACCEPT`, `GTMS_SC_*`, `GTMS_DO_*`, `GTMS_STATION_*`                              | `OUT_FOR_DELIVERY`                                        |
| **OUT_FOR_DELIVERY** | `GTMS_DELIVERING`, `SENT_SCAN`, `GTMS_RE_DELIVERING`                                 | `OUT_FOR_DELIVERY`                                        |
| **AWAITING_PICKUP**  | `GTMS_WAIT_SELF_PICK`, `GSTA_INBOUND`, `GSTAHUB_INBOUND`                             | `AWAITING_PICKUP`                                         |
| **DELIVERED**      | `GTMS_SIGNED`, `GTMS_STA_SIGNED`, `SIGNED`, `GSTA_SIGN`, `GSTA_BUYER_SIGN`, `STA_SIGN`, `COMMON_SIGNED` | `DELIVERED`                                  |
| **RETURNED**       | `RT_*`, `RETURNED`                                                                     | `EXCEPTION` *(no dedicated enum value yet)*               |
| **EXCEPTION**      | `*_FAILURE`, `*_FAIL`, `FAILED`, `REJECT`, `GWMS_EXCEPTION`                            | `EXCEPTION`                                               |
| **REROUTING**      | `GTMS_STA_*` (address-change codes)                                                    | `UNKNOWN` *(no dedicated enum value yet)*                 |
| **UNKNOWN**        | fallback (log to Crashlytics)                                                          | `UNKNOWN` → `progressRate` bucket via `mapByProgress`     |

### Codes intentionally unmapped (today)

These are recognised in the reference but **not** currently mapped to a `PackageStatus` — they fall through to `UNKNOWN` and let `progressRate` decide. List them here so it's clear they were considered and skipped, not just missed:

- `GTMS_STA_*` rerouting codes (no dedicated REROUTING enum)
- `GTMS_OTHER`, `GSTA_OTHER`, `LH_OTHER` — generic "other" buckets
- `GOT`, `DEPARTURE`, `ARRIVAL`, `GTMS_RELABEL` — light-information events
- `POSTMAN_POST` — locker endpoint, different API
- `COMMON_ACCEPT` — no concrete reference payload yet
- `CW_*` — observed but not in official enum (currently ignored by the mapper)

### Mapper-only divergences from the reference

Where our `PackageStatus` enum can't represent a Cainiao bucket, we collapse:

| Reference bucket   | Mapped to             | Why                                                                                        |
| ------------------ | --------------------- | ------------------------------------------------------------------------------------------ |
| LAST_MILE          | `OUT_FOR_DELIVERY`    | Our enum has one "Local Courier" bucket — covers both "with local carrier" and "on truck". |
| DUTIES_DUE         | `CUSTOMS_IMPORT`      | No dedicated DUTIES_DUE; it's still a customs hold, just user-actionable.                  |
| RETURNED           | `EXCEPTION`           | No dedicated RETURNED yet; EXCEPTION ensures the user notices.                             |
| REROUTING          | `UNKNOWN`             | Address-change events don't change the package's pipeline stage.                           |
