# Claude Design Prompt — BBD-AES "Hyundai Parts" ERP Web Console

> Paste everything below (from the horizontal rule down) into Claude Design as a single prompt.
> Defaults chosen for you (change at the top if you disagree): **Korean-first UI**, **vanilla HTML/CSS/JS SPA**, **MOCK_MODE on** so it renders with seeded data, with a one-switch path to the live gateway.

---

You are building the **internal web console for an automotive-parts distribution ERP** ("BBD-AES"). It is used by **Head-Office (HQ) staff** and **Branch/Dealer staff**, who log in with role-scoped accounts. The console fronts a Spring Cloud Gateway that proxies five Java microservices: **user, item (catalog), inventory, sales, procurement**. Implement a UI that exercises **every** REST endpoint listed in the API Reference (Section 9).

Build it the way **McMaster-Carr (mcmaster.com)** builds: fast, dense, plain, ruthlessly functional. No marketing fluff, no animation theater — an industrial tool that experts use all day. Information density over whitespace, instant filtering, big readable data tables, obvious primary actions, zero ambiguity about state.

## 0. Settings (tweak these constants at the top of your config)
- `LANG = "ko"` — Korean-first labels (this is a Korean company; the catalog categories come back as Korean labels). Keep code identifiers/comments in English.
- `MOCK_MODE = true` — ship realistic seeded fixtures for every resource so the app is fully navigable with no backend. Provide a single flag to switch to live calls.
- `API_BASE = "http://localhost:8080"` — the gateway.
- `CURRENCY = "KRW"` — render money as `₩1,234,000` (integer won, thousands separators, tabular figures).

## 1. Tech stack & constraints (non-negotiable)
- **Vanilla HTML5 + modern CSS + vanilla ES modules.** No React/Vue/Svelte, no build step, no CSS framework (no Bootstrap/Tailwind). This mirrors McMaster's no-framework, server-light stack.
- **Lightweight SPA**: a tiny hash-router (`#/sales/orders`, `#/inventory/stocks/{sku}`, …). Semantic HTML, real `<table>`s, real `<form>`s, progressive enhancement, full keyboard support, WCAG-AA contrast.
- **One design-token stylesheet** (`tokens.css`) consumed by a small component stylesheet. CSS custom properties only.
- **One API client module** that hides fetch, the three response envelopes, auth headers, CSRF, and error normalization (Section 5).
- Target **1280px+** desktop first (this is a back-office tool); stay usable down to ~768px. Tabular numerals everywhere numbers appear.
- Prefer instant client-side interactions: type-to-filter, debounced search, optimistic UI only where safe.

## 2. Visual design language — McMaster-Carr structure, Hyundai palette

**Brand palette (use exactly these):**
| Token | Hex | Role |
|---|---|---|
| `--hy-blue` | `#002c5f` | Primary brand. Top bar, primary buttons, links, active nav, table header text, focus ring. |
| `--hy-sand` | `#e4dcd3` | Secondary surface. Sidebar background, table header fill, chips, hover rows, dividers. |
| `--hy-light-sand` | `#f6f3f2` | App/page background, card surfaces, input backgrounds. |
| `--hy-gold` | `#a36b4f` | **"Hot stamping" premium accent — use sparingly.** Logo mark accent, the single most important KPI figure, `URGENT` priority, `BACKORDERED` attention state, selected-row left rule. Never a full button fill for routine actions. |

**Derived semantic tokens (define these, keep on-brand):**
- Text: `--ink:#1c1c1c`, `--ink-muted:#5e574f`, `--ink-inverse:#ffffff`.
- Lines: `--border:#d9cfc4`, `--border-strong:#bcae9f`.
- Surfaces: `--surface:#ffffff`, `--surface-alt:var(--hy-light-sand)`, `--surface-header:var(--hy-sand)`.
- Status: `--ok:#1f7a4d`, `--info:var(--hy-blue)`, `--attention:var(--hy-gold)`, `--danger:#b3261e`, `--muted:#8a8178`.

**Typography:** system + Korean stack — `-apple-system, "Segoe UI", Roboto, "Noto Sans KR", sans-serif`. Base **14px** (dense back-office), line-height 1.45. Headings restrained (h1 22px, h2 18px, h3 15px, all 600). `font-variant-numeric: tabular-nums` on tables, money, IDs. Monospace for order numbers (`SO-2026-0001`).

**Layout (McMaster skeleton):**
- **Fixed top bar** (`--hy-blue` fill, white text, 52px): left = logo + product name; center = global search (SKU / order# / customer); right = notifications bell (HQ), user chip (name · role · branch), logout.
- **Left sidebar** (240px, `--hy-sand`, collapsible to icons): grouped nav (Section 3), role-gated. Active item = `--hy-blue` text + `--hy-gold` left rule.
- **Content area** (`--hy-light-sand`): breadcrumb row → page title + primary action(s) → **filter bar** → **data table** → pagination. Detail = right-side drawer for quick peeks, full page for editing.
- **Cards/tables** sit on white `--surface` with `1px --border` and minimal radius (4px). Flat. Shadows almost never (a hairline border is the McMaster way).

**Component rules:**
- **Buttons:** primary = solid `--hy-blue` / white text; secondary = white with `--border-strong`; subtle = text-only blue; destructive = `--danger` outline → solid on confirm. 32px tall, 4px radius, no gradients.
- **Tables:** sticky header (`--surface-header`), 38px rows, row hover = `--hy-sand` tint, zebra optional, right-align numbers/money, sortable column headers, row-click opens detail. Show a compact density toggle (comfortable/compact).
- **Status badges:** small pill, colored dot + text. Mapping in Section 6. **Priority `URGENT`** = gold pill.
- **Forms:** label-on-top, 1-col on mobile / 2-col on desktop, inline field errors (from server validation), required markers, sticky footer with Cancel/Save. Disable/hide actions the user's role can't perform.
- **State machine controls:** render the lifecycle as a horizontal stepper on detail pages; each transition button confirms in a modal (with reason input where required) and shows the resulting status + actor + timestamp the server returns.
- **Feedback:** toast on success/error (bottom-right), skeleton rows while loading, explicit empty states ("조건에 맞는 결과가 없습니다"), and a normalized error banner for failures (Section 5).

## 3. App shell & navigation (role-gated)

```
대시보드            #/                         all
품목 (Catalog)      #/items                    all (create/price: HQ)
재고 (Inventory)
  ├ 재고 현황       #/inventory/stocks         all (branch scoped to own warehouse)
  ├ 재고 요약       #/inventory/summary        all
  ├ 입출고 내역     #/inventory/movements      all
  └ 창고 관리       #/inventory/warehouses     HQ (branch: read own)
영업 (Sales)
  ├ 수주(고객주문)   #/sales/customer-orders    all (branch scoped)
  ├ 판매주문(보충)   #/sales/orders             all (branch scoped; HQ approves)
  └ 알림함          (bell)                     HQ only
구매 (Procurement)
  ├ 발주(PO)        #/procurement/orders       HQ
  └ 공급사(Vendor)  #/procurement/vendors      HQ
관리 (Admin)
  └ 사용자 권한      #/admin/users              ADMIN
```
Branch users never see Procurement/Admin and see Sales/Inventory **scoped to their own warehouse**. Hide (don't just disable) whole modules the role can't use; disable individual actions with a tooltip explaining why.

## 4. Authentication, roles & access control

**Login flow (OIDC via the gateway — redirect-based, no password form here):**
1. On load, call `GET /api/auth/me`. If not authenticated → show a minimal **Sign-in** screen whose button does `window.location = API_BASE + "/oauth2/authorization/keycloak"` (gateway OIDC). After Keycloak, the gateway redirects back to `/main`.
2. `GET /api/csrf` once to seed the `XSRF-TOKEN` cookie; on every mutating request (POST/PUT/PATCH/DELETE) read that cookie and send header `X-XSRF-TOKEN`. Always `fetch(..., { credentials: "include" })`.
3. Resolve the current user's **role + tenancy** (HQ vs BRANCH, warehouse) — from `/api/auth/me` profile plus `GET /api/v1/users/internal/snapshot?keycloakSub=<self>` (returns `role`, `tenancyType`, `tenancyName`). Cache in a session store; gate UI off it.
4. Any `401` from an API call → clear session, redirect to sign-in. `403` → toast "권한이 없습니다" and keep the page.

**Roles** (`UserRole`): `ADMIN`, `HQ_MANAGER`, `HQ_STAFF`, `BRANCH_MANAGER`, `BRANCH_STAFF`. `ADMIN` is a superset. **HQ group** = ADMIN/HQ_MANAGER/HQ_STAFF. **Branch group** = BRANCH_MANAGER/BRANCH_STAFF (scoped to one warehouse via `tenancyName`/warehouse code). **Decision-makers** (approve/reject sales orders) = ADMIN/HQ_MANAGER only.

**Backend-compatibility shim (important — the backend is mid-migration):** some services still authorize off **temporary headers** instead of the JWT. Centralize these in the API client so they can be filled from the session and flipped off later:
- Sales service expects `X-Employee-Number`, `X-Role` (one of the role enum values), and `X-Warehouse-Code` (required for branch roles).
- Procurement expects `X-User-Id` (employee number; default `SYSTEM`).
Send them automatically based on the logged-in user. Document this clearly as a shim.

**Tenancy scoping in the UI:** for branch users, pre-fill and lock warehouse filters to their own warehouse, and on Sales/Customer-Order create, lock the warehouse field to theirs.

## 5. API integration layer (build this once, reuse everywhere)

**Three different response conventions live behind one gateway — normalize them:**
1. **Sales & Inventory & Item & User**: success returns the **raw JSON** (no envelope).
2. **Procurement**: success is wrapped — `{ "code": "SUCCESS", "message": "...", "data": <T> }`. Unwrap `.data`; surface `.message` in the success toast.
3. **Errors**: two shapes — RFC-7807 **ProblemDetail** (`application/problem+json`: `status`, `title`, `detail`, plus a `code` like `SO004`/`CO002`/`I001`) on sales/inventory/item/user; and `ApiResponse` with a non-`SUCCESS` `code`/`message` on procurement. Normalize both into `{ httpStatus, code, message }` for a single error handler → field-level form errors when it's a 400 validation failure, otherwise a toast/banner.

**Three pagination conventions — normalize to one `{items, page, size, totalElements, totalPages}`:**
- Sales lists → `{ items, pagination:{ page, size, totalElements, totalPages } }`.
- Inventory/Item lists → `PageResponse { content, page, size, totalElements, totalPages, (item adds first/last/hasNext/hasPrevious) }`.
- Procurement lists → **plain array** in `data` (no server pagination → paginate client-side).

**Routing note (verify before going live — known mismatch):** the gateway routes by service prefix (`/api/v1/users/**`, `/api/v1/items/**`, `/api/v1/inventory/**`, `/api/v1/procurements/**`, `/api/v1/sales/**` with an added `/sales` prefix), but several controllers currently mount their own paths (`/api/v1/customer-orders`, `/api/v1/sales-orders`, `/api/v1/stocks`, `/api/v1/warehouses`, `/api/v1/purchase-orders`, `/api/v1/vendors`). **Put every path in one `ROUTES` map** so the effective gateway path is a one-line change per resource. Default the map to the controller-native paths (Section 9) and leave a comment flagging the gateway-prefix translation.

**Query-param casing:** sales list filters are **snake_case** (`dealer_warehouse_code`, `to_warehouse_code`, `customer_name`, `requested_by`, `start_date`, `end_date`) while bodies are camelCase. Inventory/item use camelCase + `page`/`size`. Handle per-endpoint.

**MOCK_MODE:** when on, the client resolves against in-memory fixtures (seed ~25 items across the 15 categories, ~6 warehouses HQ+DEALER, stock rows with some below-safety, ~12 customer orders and ~12 sales orders spanning every status, ~8 POs, ~6 vendors, HQ notifications, and current-user fixtures for one HQ and one branch persona with a persona switcher in dev). All writes mutate the fixtures so workflows are demoable end-to-end.

## 6. Modules & page specs (each must call the listed endpoints)

### 6.1 Dashboard (`#/`)
Role-aware KPI strip + recent activity. There are no dedicated dashboard endpoints — **compose KPIs from list `totalElements` and the stock summary:**
- **All:** stock summary card grid from `GET /api/v1/stocks/summary` (총 창고/활성 창고/총 SKU/총 재고/안전재고 미달 건수 — render 미달 건수 in `--hy-gold` if > 0).
- **HQ:** "승인 대기 판매주문" = `GET /api/v1/sales-orders?status=SUBMITTED&size=1` → `totalElements`; "백오더" = `status=BACKORDERED`; "DRAFT 발주" = `GET /api/v1/purchase-orders` count. Each KPI links to the filtered list.
- **Branch:** "내 진행중 보충요청" (REQUESTED/SUBMITTED/IN_FULFILLMENT for own warehouse); "도착 대기(수령)" = IN_FULFILLMENT; "열린 수주" = customer orders OPEN.
- **Recent activity:** latest rows from `GET /api/v1/stock-movements?size=10`.

### 6.2 Catalog — 품목 (`#/items`)
- **List/search:** `GET /api/v1/items/filter` — filter bar: name (debounced text), `category` (dropdown of the 15 Korean labels), `active` (전체/활성/비활성); sort by `name`/`sku` ASC/DESC; paginated. Columns: SKU(mono), 품목명, 카테고리(label), 단위(EA/SET), 안전재고, 단가(₩), 조달구분(MAKE=생산/BUY=구매), 상태(활성/비활성 badge).
- **Detail** (`#/items/{sku}`): `GET /api/v1/items/{sku}` → spec table (McMaster product-detail feel). Show "이 품목의 창고별 재고" by also calling `GET /api/v1/stocks/{sku}`.
- **Create** (HQ_MANAGER/HQ_STAFF): modal → `POST /api/v1/items` (`sku, name, category, unit, safetyStock, unitPrice, active, sourcingType`). 201 → no body, refetch.
- **Update price** (HQ): inline/modal → `PATCH /api/v1/items/{sku}/price` `{unitPrice}`.
- Categories (constant → Korean label) for the dropdown: ENGINE_OIL 엔진/오일, ENGINE_FILTER 엔진/필터, IGNITION 점화, BRAKE 제동, POWERTRAIN 동력전달, SUSPENSION_STEERING 현가/조향, ELECTRICAL 전장, COOLING 냉각, AIR_CONDITIONING 공조/에어컨, EXHAUST 배기, TIRE_WHEEL 타이어/휠, EXTERIOR 외장, INTERIOR 내장, WIPER_WASHER 와이퍼/워셔, ACCESSORY_ETC 용품/기타. **Filter sends the constant; responses show the label.** (Batch lookup `GET /api/v1/items` with `{skuList}` body exists for enriching order lines — use it internally to show item names where only SKUs are returned.)

### 6.3 Inventory — 재고
- **재고 현황** (`#/inventory/stocks`): `GET /api/v1/stocks` — filters warehouseCode, sku, category, `belowSafety` (toggle "안전재고 미달만"); paged. Columns: SKU, 품목명, 현재고, 창고. **Below-safety rows flagged** (gold left rule + ⚠). Row → SKU detail.
- **SKU 재고 상세** (`#/inventory/stocks/{sku}`): `GET /api/v1/stocks/{sku}` — header (품목명/카테고리/단위/안전재고/단가) + per-warehouse table (창고/현재고/가용재고/로케이션/수정시각). Per-warehouse "로케이션 변경" → `PATCH /api/v1/stocks/{warehouseCode}/{sku}/location` `{location}` (branch staff for own warehouse, or HQ).
- **단일 창고×SKU**: `GET /api/v1/stocks/{warehouseCode}/{sku}` for a focused view (현재고/가용/안전/단가/로케이션).
- **재고 요약** (`#/inventory/summary`): `GET /api/v1/stocks/summary` — KPI grid + per-active-warehouse breakdown table (code/name/type/skuCount/currentStock).
- **입출고 내역** (`#/inventory/movements`): `GET /api/v1/stock-movements` — filters movementType (IN/OUT), sku, warehouseCode, from/to (datetime, occurredAt range); paged, default sort occurredAt desc. Columns: 일시, 구분(IN 입고/OUT 출고 badge), SKU, 창고, 수량, 단가, 참조번호(SO/PO, link). Row → `GET /api/v1/stock-movements/{id}`.
- **창고 관리** (`#/inventory/warehouses`, HQ): `GET /api/v1/warehouses` (filter active, type HQ/DEALER; paged). Detail `GET /api/v1/warehouses/{code}`. Create `POST /api/v1/warehouses` `{code,name,type,address}` (returns the code string). Update `PUT /api/v1/warehouses/{code}` `{name,type,address}` (204). Activate/deactivate `PATCH /api/v1/warehouses/{code}/activate|deactivate` (204) with an active/inactive badge + toggle.

### 6.4 Sales — 영업

**수주 / 고객주문 (CustomerOrder)** — a branch takes an order from an end customer.
- **List** (`#/sales/customer-orders`): `GET /api/v1/customer-orders` — filters: status, dealer_warehouse_code (locked to own for branch), customer_name, requested_by, start_date/end_date, page/size. Columns: CO번호(mono), 대리점창고, 대리점명, 고객명, 상태 badge, 요청자, 요청일, 합계(₩). 
- **Detail** (`#/sales/customer-orders/{coNumber}`): `GET /api/v1/customer-orders/{coNumber}` — header + lines table (lineNo, SKU, 품목명 snapshot, 단가 snapshot, 수량). Lifecycle stepper.
- **Create**: `POST /api/v1/customer-orders` `{dealerWarehouseCode, customerName, customerContact?, note?, lines:[{sku,quantity}]}` — line editor with SKU autocomplete (from catalog) + qty; branch warehouse locked to own; ADMIN can choose. 201 → detail.
- **Edit** (OPEN only): `PUT /api/v1/customer-orders/{coNumber}` `{note?, lines?}`.
- **Transitions** (PATCH, no body): `/confirm` (OPEN→CONFIRMED), `/cancel` (OPEN|CONFIRMED→CANCELED), `/close` (CONFIRMED→CLOSED). Each returns `{coNumber,status,actor,changedAt}` → update stepper + toast. Show only the buttons valid for the current status & role (own-warehouse branch or ADMIN).
- **Status colors:** OPEN=info(blue) · CONFIRMED=ok(green) · CLOSED=muted · CANCELED=danger.

**판매주문 / 보충요청 (SalesOrder)** — the core HQ↔branch replenishment workflow (branch requests stock; HQ reserves/produces/purchases; branch receives).
- **List** (`#/sales/orders`): `GET /api/v1/sales-orders` — filters status, priority, to_warehouse_code (locked for branch), requested_by, date range, paging. Columns: SO번호(mono), 도착창고, 상태 badge, 우선순위(URGENT=gold pill), 요청자, 요청일, 합계(₩).
- **Detail** (`#/sales/orders/{soNumber}`): `GET /api/v1/sales-orders/{soNumber}` — header + `rejectedReason` when present + lines (lineNo, SKU, 품목명, 단가, 수량, **예약수량 reservedQuantity, 조달원 fulfillmentSource STOCK/PRODUCTION/PURCHASE, 출고창고 fromWarehouseCode**). Prominent lifecycle stepper.
- **Create**: `POST /api/v1/sales-orders` `{toWarehouseCode, priority(NORMAL|URGENT), note?, lines:[{sku,quantity}]}`.
- **Edit** (REQUESTED only): `PUT /api/v1/sales-orders/{soNumber}` `{priority?, note?, lines?}`.
- **Transitions** (all PATCH, all return `{soNumber,status,actor,changedAt,reason}`):
  - `/submit` REQUESTED→SUBMITTED — **BRANCH_MANAGER** of owning warehouse or ADMIN.
  - `/cancel` REQUESTED|SUBMITTED→CANCELED — own-warehouse branch or ADMIN.
  - `/approve` SUBMITTED→IN_FULFILLMENT **or** BACKORDERED — **ADMIN/HQ_MANAGER** only. Tell the user the result (fulfilled vs backordered).
  - `/reject` SUBMITTED→REJECTED — ADMIN/HQ_MANAGER; **requires a reason** (modal textarea; blank → server 400 `SO006`); show `reason` afterward.
  - `/fulfill-backorder` BACKORDERED→IN_FULFILLMENT (or stays BACKORDERED if still short — idempotent) — ADMIN/HQ_MANAGER.
  - `/receive` IN_FULFILLMENT→RECEIVED — own-warehouse branch or ADMIN (triggers the real stock transfer).
- **Status colors:** REQUESTED=muted · SUBMITTED=info · IN_FULFILLMENT=ok · **BACKORDERED=attention(gold)** · REJECTED=danger · CANCELED=muted · RECEIVED=ok(solid). Render the stepper so branch and HQ each see what they must do next.

**알림함 (HQ notifications):** bell in the top bar (HQ only) → `GET /api/v1/notifications` (up to 100 unread for HQ). Show count, dropdown list (message, soNumber link, createdAt). Clicking navigates to that SO. (Note: the `read` flag exists but there is **no mark-as-read endpoint** — keep it read-only and just deep-link; flag this as a backend gap.)

### 6.5 Procurement — 구매 (HQ)
- **발주 (PurchaseOrder)** (`#/procurement/orders`): list `GET /api/v1/purchase-orders` (summary array → client-paginate). Columns: PO번호(mono), 공급사, 상태(DRAFT=info/RECEIVED=ok/CANCELED=muted), 합계(₩), 입고예정일, 생성일. Detail `GET /api/v1/purchase-orders/{poNumber}` (header + lines: lineOrder, SKU, 부품명, 단가, 수량, 소계). Create `POST` `{vendorCode, warehouseCode, soNumber?, expectedArrival?, note?, lines:[{lineOrder,sku,quantity}]}` (201, sends `X-User-Id`). Edit header `PATCH /{poNumber}`. Replace lines `PUT /{poNumber}/lines` `{lines:[…]}`. Complete `POST /{poNumber}/complete` (DRAFT→RECEIVED). Cancel `POST /{poNumber}/cancel` (DRAFT→CANCELED). **History** `GET /{poNumber}/history` → timeline (changeType CREATED/HEADER_UPDATED/LINES_REPLACED/COMPLETED/CANCELED, changedBy, changedAt, before/after JSON diff). 
  - **Draft-from-SO:** "판매주문으로 발주 생성" → `GET /api/v1/sales-orders/{soNumber}` (procurement relay) prefills vendor/warehouse/lines.
- **공급사 (Vendor)** (`#/procurement/vendors`): list `GET /api/v1/vendors` (code, name, active). Detail `GET /api/v1/vendors/{code}` (code,name,contact,terms,active,timestamps). Create `POST` `{name,contact?,terms?}` (HQ_MANAGER). Update `PATCH /{code}` `{name,contact?,terms?}`. Toggle `PATCH /{code}/active` `{active:boolean}` with an active/inactive switch.

### 6.6 Administration — 관리 (ADMIN)
- **사용자 권한 (User authorization)** (`#/admin/users`): `PATCH /api/v1/users/{userId}/authorization` `{status, role, tenancyType, tenancyName?}` → returns the full `UserSnapshotResponse`. Provide a form: enter userId, pick status (ACTIVE/PENDING/INACTIVE), role (5 values), tenancyType (HQ/BRANCH), tenancyName; show the resulting snapshot card. **Backend gap:** there is no "list users" endpoint exposed to the browser (user listing/provisioning happens via SCIM/mTLS from an external IdP, which is **out of scope for this UI**). Note this limitation in the page; design around a userId lookup + the self-snapshot (`GET /api/v1/users/internal/snapshot?keycloakSub=`).

**Excluded from the UI:** all `/scim/**` endpoints (machine-to-machine provisioning over mTLS), `/health`, and `/api/error` test endpoints. You may add a tiny footer "시스템 상태" dot that pings `/health` per service if you like, but it is optional.

## 7. Cross-cutting UX requirements
- **Tables:** server-driven pagination where the API supports it (sales/inventory/item); client-side for procurement arrays. Column sort, sticky header, density toggle, row-click → detail, right-aligned numerics, copy-to-clipboard on order numbers.
- **Filter bars:** persist filters in the URL hash query so views are shareable/bookmarkable; "초기화" clears; debounce text inputs (~250ms).
- **Forms:** map server validation (`@NotBlank`/`@Min`/etc. → 400) to field errors; disable submit while pending; confirm destructive/irreversible transitions.
- **State transitions:** always reflect the server's returned status/actor/timestamp; never optimistically assume success for money/stock-affecting actions (approve/receive/complete) — show a spinner and reconcile from the response.
- **Money/date:** `₩` + thousands separators (integer KRW); dates `YYYY-MM-DD`, datetimes `YYYY-MM-DD HH:mm`. 
- **Empty / loading / error** states for every list and detail. Global error banner for 5xx; toast for 4xx; redirect for 401.
- **Accessibility & speed:** keyboard navigation for tables and dialogs, focus management, `aria` on badges/steppers, no layout shift, sub-100ms interactions in MOCK_MODE.

## 8. Role × capability matrix (gate the UI to this)
| Capability | ADMIN | HQ_MANAGER | HQ_STAFF | BRANCH_MANAGER | BRANCH_STAFF |
|---|---|---|---|---|---|
| View catalog / inventory | ✅ | ✅ | ✅ | ✅ (own wh) | ✅ (own wh) |
| Create item / update price | ✅ | ✅ | ✅ | — | — |
| Warehouse CRUD / activate | ✅ | ✅ | ✅ | — (read) | — (read) |
| Update stock location | ✅ | ✅ | ✅ | ✅ (own wh) | ✅ (own wh) |
| Customer order create/edit/confirm/cancel/close | ✅ | ✅* | ✅* | ✅ (own wh) | ✅ (own wh) |
| Sales order create/edit/cancel | ✅ | ✅* | ✅* | ✅ (own wh) | ✅ (own wh) |
| Sales order **submit** | ✅ | — | — | ✅ (own wh) | — |
| Sales order **approve/reject/fulfill-backorder** | ✅ | ✅ | — | — | — |
| Sales order **receive** | ✅ | — | — | ✅ (own wh) | ✅ (own wh) |
| HQ notifications | ✅ | ✅ | ✅ | — | — |
| Purchase orders (all) | ✅ | ✅ | ✅ | — | — |
| Vendor create/update/toggle | ✅ | ✅ | — (read) | — | — |
| User authorization | ✅ | — | — | — | — |

\* HQ acting on a specific branch's order is allowed for ADMIN; for HQ_MANAGER/HQ_STAFF treat branch-scoped create/edit as primarily a branch action and gate by the server's response (it enforces own-warehouse/ADMIN). When in doubt, attempt the call and handle 403 gracefully.

## 9. Complete API reference (implement all of these)

> Base = gateway `http://localhost:8080`. Paths below are the **controllers' native paths** (put them in the `ROUTES` map; reconcile the gateway prefix per Section 5). Auth = session cookie + CSRF header on writes; plus the compat headers in Section 4.

**USER** (raw JSON)
- `GET /api/auth/me` — current login state + profile (gateway).
- `GET /api/csrf` — seed CSRF cookie (gateway).
- `POST /logout` — RP-initiated logout (gateway).
- `PATCH /api/v1/users/{userId}/authorization` — body `{status:UserStatus, role:UserRole, tenancyType:TenancyType, tenancyName?}` → `UserSnapshotResponse{userId,keycloakSub,employeeNumber,username,displayName,email,position,status,role,tenancyType,tenancyName,version}`. [ADMIN]
- `GET /api/v1/users/internal/snapshot?keycloakSub=` — self only → `UserSnapshotResponse`.
- *(excluded: `/scim/**`, `/health`)*

**ITEM** (raw JSON)
- `POST /api/v1/items` — `{sku,name,category,unit,safetyStock,unitPrice,active,sourcingType}` → 201 no body. [HQ_MANAGER/HQ_STAFF]
- `PATCH /api/v1/items/{sku}/price` — `{unitPrice}` → 200 no body. [HQ]
- `GET /api/v1/items/{sku}` → `ItemResponse{sku,name,category(label),unit,safetyStock,unitPrice,active,sourcingType}`.
- `GET /api/v1/items` — body `{skuList:[]}` (batch lookup) → `ItemResponse[]`. (internal enrichment)
- `GET /api/v1/items/filter?page&size&sortBy(name|sku)&direction(ASC|DESC)&name&category&active` → `PageResponse{content,page,size,totalElements,totalPages,first,last,hasNext,hasPrevious}`.
- Enums: `Category` (15, see 6.2), `Unit` EA/SET, `SourcingType` MAKE/BUY.

**INVENTORY** (raw JSON; lists = `PageResponse{content,page,size,totalElements,totalPages}`)
- `GET /api/v1/stocks?warehouseCode&sku&category&belowSafety&page&size&sort` → items `{sku,name,currentStock,warehouseCode}`.
- `GET /api/v1/stocks/{warehouseCode}/{sku}` → `{sku,name,category,unit,warehouseCode,currentStock,availableStock,safetyStock,unitPrice,location,updatedAt}`.
- `GET /api/v1/stocks/summary` → `{totalWarehouses,activeWarehouses,totalSkus,totalCurrentStock,totalAvailableStock,belowSafetyCount,warehouses:[{code,name,type,active,skuCount,currentStock}]}`.
- `GET /api/v1/stocks/{sku}` → `{sku,name,category,unit,safetyStock,unitPrice,warehouses:[{warehouseCode,currentStock,availableStock,location,updatedAt}]}`.
- `PATCH /api/v1/stocks/{warehouseCode}/{sku}/location` — `{location}` → `{sku,warehouseCode,location,updatedAt}`.
- `GET /api/v1/stock-movements?movementType(IN|OUT)&sku&warehouseCode&from&to&page&size` → items `{id,type,sku,warehouseCode,quantity,unitPrice,referenceNumber,occurredAt}`.
- `GET /api/v1/stock-movements/{id}` → `MovementResponse`.
- `POST /api/v1/warehouses` — `{code,name,type(HQ|DEALER),address?}` → 201 string(code).
- `PUT /api/v1/warehouses/{code}` — `{name,type,address?}` → 204.
- `PATCH /api/v1/warehouses/{code}/activate` → 204 · `PATCH /api/v1/warehouses/{code}/deactivate` → 204.
- `GET /api/v1/warehouses/{code}` → `{code,name,type,address,active}`.
- `GET /api/v1/warehouses?active&type&page&size` → `PageResponse<WarehouseResponse>`.
- Enums: `MovementType` IN/OUT, `WarehouseType` HQ/DEALER. (no auth enforced server-side; gate in UI per Section 8)

**SALES** (raw JSON; lists = `{items, pagination:{page,size,totalElements,totalPages}}`; **filters snake_case**; identity via `X-Employee-Number`/`X-Role`/`X-Warehouse-Code`)
- CustomerOrder base `/api/v1/customer-orders`:
  - `GET ` `?status&dealer_warehouse_code&customer_name&requested_by&start_date&end_date&page&size` → summaries `{coNumber,dealerWarehouseCode,dealerName,customerName,status,requestedBy,confirmedBy,canceledBy,closedBy,requestedAt,confirmedAt,canceledAt,closedAt,totalAmount,note}`.
  - `POST ` → 201 `CustomerOrderDetailResponse`. body `{dealerWarehouseCode,customerName,customerContact?,note?,lines:[{sku,quantity}]}`.
  - `GET /{coNumber}` → detail `{coNumber,dealerWarehouseCode,customerName,customerContact,status,requestedBy,confirmedBy,canceledBy,closedBy,requestedAt,confirmedAt,canceledAt,closedAt,totalAmount,note,lines:[{lineNo,sku,nameSnapshot,unitPriceSnapshot,quantity}]}`.
  - `PUT /{coNumber}` — `{note?,lines?}` (OPEN only).
  - `PATCH /{coNumber}/confirm` · `/cancel` · `/close` → `{coNumber,status,actor,changedAt}`.
  - Status: OPEN→CONFIRMED→CLOSED, +CANCELED.
- SalesOrder base `/api/v1/sales-orders`:
  - `GET ` `?status&priority&to_warehouse_code&requested_by&start_date&end_date&page&size` → summaries `{soNumber,toWarehouseCode,toWarehouseName,status,priority,requestedBy,approvedBy,receivedBy,canceledBy,requestedAt,approvedAt,receivedAt,canceledAt,totalAmount,note}`.
  - `POST ` → 201 detail. body `{toWarehouseCode,priority(NORMAL|URGENT),note?,lines:[{sku,quantity}]}`.
  - `GET /{soNumber}` → detail `{…,rejectedReason,lines:[{lineNo,sku,nameSnapshot,unitPriceSnapshot,quantity,reservedQuantity,fulfillmentSource(STOCK|PRODUCTION|PURCHASE),fromWarehouseCode}]}`.
  - `PUT /{soNumber}` — `{priority?,note?,lines?}` (REQUESTED only).
  - `PATCH /{soNumber}/submit` (BRANCH_MANAGER/ADMIN) · `/cancel` · `/approve` (ADMIN/HQ_MANAGER) · `/reject` body `{reason}` (ADMIN/HQ_MANAGER) · `/fulfill-backorder` (ADMIN/HQ_MANAGER) · `/receive` → `{soNumber,status,actor,changedAt,reason}`.
  - Status: REQUESTED→SUBMITTED→(IN_FULFILLMENT|BACKORDERED)→RECEIVED, +REJECTED, +CANCELED. Priority NORMAL/URGENT.
- Notifications: `GET /api/v1/notifications` (HQ) → `[{id,targetRole,soNumber,message,read,createdAt}]`.

**PROCUREMENT** (envelope `{code,message,data}`; lists = plain array in `data`; identity via `X-User-Id`)
- PurchaseOrder base `/api/v1/purchase-orders`:
  - `POST ` → 201 `PurchaseOrderResponse`. body `{vendorCode,warehouseCode,soNumber?,expectedArrival?,note?,lines:[{lineOrder,sku,quantity}]}`.
  - `PATCH /{poNumber}` — header `{vendorCode,warehouseCode,soNumber?,expectedArrival?,note?}`.
  - `PUT /{poNumber}/lines` — `{lines:[{lineOrder,sku,quantity}]}`.
  - `POST /{poNumber}/complete` (DRAFT→RECEIVED) · `POST /{poNumber}/cancel` (DRAFT→CANCELED).
  - `GET /{poNumber}` → `PurchaseOrderResponse{poNumber,vendorCode,warehouseCode,soNumber,status,totalAmount,expectedArrival,note,createdBy,receivedBy,createdAt,updatedAt,receivedAt,lines:[{lineOrder,sku,partName,unitPrice,quantity,subtotal}]}`.
  - `GET ` → `PurchaseOrderSummaryResponse[]` `{poNumber,vendorCode,status,totalAmount,expectedArrival,createdAt}`.
  - `GET /{poNumber}/history` → `[{changeType,changedBy,changedAt,before,after}]` (changeType: CREATED/HEADER_UPDATED/LINES_REPLACED/COMPLETED/CANCELED).
  - Status DRAFT/RECEIVED/CANCELED.
- `GET /api/v1/sales-orders/{soNumber}` (procurement relay) → `SalesOrderRelayResponse{soNumber,fromWarehouseCode,fromWarehouseName,toWarehouseCode,toWarehouseName,status,priority,requestedBy,approvedBy,requestedAt,approvedAt,totalAmount,note,lines:[{lineNo,sku,nameSnapshot,unitPriceSnapshot,quantity}]}` — prefill PO.
- Vendor base `/api/v1/vendors`:
  - `POST ` → 201 `VendorResponse`. body `{name,contact?,terms?}`.
  - `PATCH /{code}` — `{name,contact?,terms?}` · `PATCH /{code}/active` — `{active:boolean}`.
  - `GET /{code}` → `{code,name,contact,terms,active,createdAt,updatedAt}` · `GET ` → `VendorSummaryResponse[]{code,name,active}`.

## 10. Deliverables
Produce a self-contained app:
```
index.html                  app shell (top bar, sidebar, content outlet)
css/tokens.css              palette + type + spacing tokens
css/app.css                 components (tables, badges, forms, stepper, drawer, toast)
js/api.js                   fetch wrapper: ROUTES map, envelopes, pagination, CSRF, compat headers, error normalize, MOCK_MODE
js/auth.js                  session/role/tenancy, login redirect, 401 handling, role-gating helpers
js/router.js                hash router + URL-query filter state
js/components/*.js          table, filterbar, pagination, badge, stepper, modal, toast, drawer
js/views/*.js               one module per file: dashboard, items, inventory, sales-customer-orders, sales-orders, procurement-orders, vendors, admin-users, notifications
js/mock/fixtures.js         seeded data + two personas (HQ, branch) + dev persona switcher
```
Make the **sales-order lifecycle** (create → submit → approve/backorder → fulfill-backorder → receive) and the **purchase-order lifecycle** (create → lines → complete/cancel + history) fully demoable end-to-end in MOCK_MODE. Ship it looking like a tool McMaster-Carr would be proud of, painted in Hyundai colors.
