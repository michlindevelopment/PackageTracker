# Cainiao actionCode Reference

**Source:** Alibaba TOP API — `aliexpress.logistics.redefining.querytrackingresult`
**Doc URL:** https://developer.alibaba.com/docs/api.htm?scopeId=12782&apiId=30120

> Official enum ends with "等" (etc) — list NOT exhaustive.

---

## DISPATCH / ORIGIN

| Code | Meaning |
|---|---|
| `CONSIGN` | Shipment declared |
| `OM_CONSIGN` | Merchant dispatched |
| `GTMS_ASN` | Electronic advance notice (ASN) received |

## PICKUP (PU_*)

| Code | Meaning |
|---|---|
| `PU_PICKUP_SUCCESS` | Pickup OK |
| `PU_PICKUP_FAILURE` | Pickup failed |
| `PU_SIGN_IN_SUCCESS` | Courier signed parcel in at origin pickup point |
| `PU_SIGN_IN_FAILURE` | Pickup sign fail |

## SORTING CENTER ORIGIN (SC_*)

| Code | Meaning |
|---|---|
| `SC_INBOUND_SUCCESS` | Inbound at sorting center |
| `SC_INBOUND_FAILURE` | SC inbound fail |
| `SC_OUTBOUND_SUCCESS` | Outbound from sorting center |
| `SC_OUTBOUND_FAILURE` | SC outbound fail |
| `SC_HO_OUT_SUCCESS` | SC handed to line-haul OK |
| `SC_HO_OUT_FAILURE` | SC handoff fail |
| `SC_SIGN_IN_SUCCESS` | SC sign-in OK |
| `SC_SIGN_IN_FAILURE` | SC sign-in fail |

## TRANSIT HUB (TD_*)

| Code | Meaning |
|---|---|
| `TD_TRANS_ARRIVE` | Arrived transit hub |
| `TD_TRANS_DEPART` | Left transit hub |

## EXPORT CUSTOMS (CC_EX_*)

| Code | Meaning |
|---|---|
| `CC_EX_START` | Export clearance started |
| `CC_EX_SUCCESS` | Export cleared |
| `CC_EX_FAILURE` | Export clearance failed |

## LINE HAUL / AIRLINE (LH_*)

| Code | Meaning |
|---|---|
| `LH_HO_IN_SUCCESS` | Handover-in OK (arrived hub) |
| `LH_HO_IN_FAILURE` | Handover-in fail |
| `LH_POST_COLLECTION` | Post collection handover fail |
| `LH_HO_AIRLINE` | Handed to airline / aviation security OK |
| `LH_HO_AIRLINE_FAILURE` | Aviation security fail |
| `LH_DEPART` | Flight departed origin |
| `LH_ARRIVE` | Flight arrived destination country |
| `LH_ARRIVE_FAILURE` | Line-haul arrival fail |
| `LH_HO_OUT_SUCCESS` | Line-haul handover-out OK |
| `LH_HO_OUT_FAILURE` | Line-haul handover-out fail |
| `LH_OTHER` | Other line-haul event |

## TRANSIT COUNTRY CLEARANCE (CC_TRANS_*)

| Code | Meaning |
|---|---|
| `CC_TRANS_START` | Transit-country clearance started |
| `CC_TRANS_SUCCESS` | Transit-country clearance OK |
| `CC_TRANS_FAILURE` | Transit-country clearance failed |

## IMPORT CUSTOMS (CC_IM_*, CC_HO_*, CUS_*, CIQ_*)

| Code | Meaning |
|---|---|
| `CC_IM_START` | Destination clearance started |
| `CC_IM_PROCESS` | Destination clearance in progress |
| `CC_IM_SUCCESS` | Destination cleared |
| `CC_IM_FAILURE` | Destination clearance fail |
| `CC_HO_IN_SUCCESS` | Customs handover-in OK |
| `CC_HO_IN_FAILURE` | Customs handover-in fail |
| `CC_HO_OUT_SUCCESS` | Customs handover-out OK |
| `CC_HO_OUT_FAILURE` | Customs handover-out fail |
| `CUS_DECLARE` | Customs declaration submitted |
| `CUS_SUCCESS` | Customs review passed |
| `CUS_FAILURE` | Customs review fail |
| `CUS_TAX` | Customs tax assessed (duties due) |
| `CIQ_SUCCESS` | Inspection & quarantine OK |
| `CIQ_FAILURE` | Inspection & quarantine fail |

## DESTINATION DELIVERY (GTMS_*)

| Code | Meaning |
|---|---|
| `GTMS_ACCEPT` | Arrived destination country / accepted by local carrier |
| `GTMS_SC_ARRIVE` | Arrived destination sorting center |
| `GTMS_SC_ARRIVE_FAILURE` | Destination SC arrival fail |
| `GTMS_DO_ARRIVE` | Arrived last delivery station |
| `GTMS_DO_DEPART` | Left last delivery station |
| `GTMS_DELIVERING` | Out for delivery |
| `GTMS_DEL_FAILURE` | Delivery failed |
| `GTMS_RE_DELIVERING` | Re-delivery attempt |
| `GTMS_RELABEL` | Re-labeled / transferred |
| `GTMS_SIGNED` | Signed by recipient |
| `GTMS_SIGN_FAILURE` | Signature fail |
| `GTMS_WAIT_SELF_PICK` | Awaiting customer self-pickup |
| `GTMS_STA_SIGNED` | Parcel signed IN at pickup station (arrived — awaiting customer) |
| `GTMS_STA_SIGN_FAILURE` | Pickup-station sign fail |
| `GTMS_STATION_IN` | Foreign delivery node (in) |
| `GTMS_STATION_OUT` | Foreign delivery node (out) |
| `GTMS_FAILURE` | Delivery exception |
| `GTMS_OTHER` | Other GTMS event |

## RE-ROUTING (address changes)

| Code | Meaning |
|---|---|
| `GTMS_STA_WAIT_CUST_CHANGE_ADDRESS` | Awaiting consumer to update address |
| `GTMS_STA_CUST_TO_DOOR` | Consumer updated recipient address |
| `GTMS_STA_CHANGE` | Consumer updated pickup-point address |
| `GTMS_STA_CN_TO_DOOR` | System updated recipient address |
| `GTMS_STA_CN_CHANGE` | System updated pickup-point address |
| `GTMS_STA_CNCP_TO_DOOR` | Carrier updated recipient address |
| `GTMS_STA_CNCP_CHANGE` | Carrier updated pickup-point address |

## PICKUP POINT / SELF PICKUP (GSTA_*, STA_*)

| Code | Meaning |
|---|---|
| `GSTA_SIGN` | User signed at pickup point |
| `GSTA_SIGN_FAIL` | User sign fail at pickup point |
| `STA_SIGN` | Pickup-point sign (domestic CN) |
| `STA_SIGN_FAIL` | Pickup-point sign fail (domestic CN) |
| `GSTAHUB_INBOUND` | Pickup distribution-center inbound OK |
| `GSTAHUB_INBOUND_FAIL` | Pickup distribution-center inbound fail |
| `GSTA_INBOUND` | Pickup-point inbound OK |
| `GSTA_INBOUND_FAIL` | Pickup-point inbound fail |
| `GSTA_BUYER_SIGN` | Buyer signed at pickup point |
| `GSTA_BUYER_SIGN_FAIL` | Buyer sign fail at pickup point |
| `GSTA_INFORM_BUYER` | Notify buyer |
| `GSTA_OTHER` | Other pickup-point event |

## DOMESTIC (CHINA) DELIVERY

| Code | Meaning |
|---|---|
| `GOT` | Domestic pickup OK |
| `REJECT` | Domestic pickup fail |
| `DEPARTURE` | Domestic node (depart) |
| `ARRIVAL` | Domestic node (arrive) |
| `SENT_SCAN` | Out for delivery (domestic) |
| `SIGNED` | Delivered (domestic) |
| `FAILED` | Delivery fail (domestic) |

## GLOBAL WAREHOUSE (GWMS_*)

| Code | Meaning |
|---|---|
| `GWMS_ACCEPT` | Order accepted |
| `GWMS_REJECT` | Order accept fail |
| `GWMS_PICK` | Picking complete |
| `GWMS_PACKAGE` | Packed |
| `GWMS_OUTBOUND` | Outbound OK |
| `GWMS_HANDOVER` | Handover OK |
| `GWMS_EXCEPTION` | Warehouse exception |
| `GWMS_CANCEL` | Cancelled |

## RETURNS (RT_*)

| Code | Meaning |
|---|---|
| `RT_INBOUND` | Returned to seller (overseas CP warehouse) |
| `RETURNED` | Return delivered to seller |
| `RT_OUTBOUND` | Overseas CP outbound |
| `RT_DESTROY` | Overseas CP destroyed |
| `RT_HO_OUT` | CP handed out |
| `RT_WH_HO_IN` | Return-warehouse received |
| `RT_WH_HO_IN_FAIL` | Return-warehouse receive fail |
| `RT_WH_INBOUND` | Return-warehouse inbound |
| `RT_WH_INBOUND_FAILURE` | Return-warehouse inbound fail |
| `RT_WH_OUTBOUND` | Return-warehouse outbound |
| `RT_WH_HO_OUT` | Return-warehouse handed out |
| `RT_WH_DESTROY` | Destroyed in return warehouse |
| `RT_TRANSWH_HO_IN` | Transit warehouse received |
| `RT_TRANSWH_INBOUND` | Transit warehouse inbound |
| `RT_TRANSWH_OUTBOUND` | Transit warehouse outbound |
| `RT_TMS_ACCEPT` | Domestic carrier accepted return |

## LOCKER (cainiao.endpoint.locker.top.order.tracking API)

| Code | Meaning |
|---|---|
| `POSTMAN_POST` | Postman deposited package in locker |
