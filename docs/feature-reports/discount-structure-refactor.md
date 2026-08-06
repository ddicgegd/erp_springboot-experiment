# Discount Structure Refactor Research

## Current State

Discount is currently represented in several places with different meanings:

- `Product.discountPercent`, `discountStartDate`, `discountEndDate`: product-level campaign metadata, accepted by DTOs but not applied when calculating cart/order totals.
- `Attributes.salePrice`: variant-level effective selling price, used by cart and order flows as the real payable unit price.
- `ShoppingCart.totalDiscount`: derived value, currently `totalPrice - totalSalePrice`.
- `Order.discountAmount` and `discountCode`: order-level coupon fields, stored but no discount-code validation or pricing rule exists.
- `OrderItem.discountAmount` and `discountPercentage`: snapshot fields exist, but `OrderService.buildItem` does not populate them.

The actual pricing source today is `Attributes.salePrice`. Product discount dates/percent are mostly passive metadata.

## Problems

1. No single discount calculation boundary

   Cart calculation lives in `Merchandise.Helper.effectiveSalePrice`, while order calculation repeats its own sale-price logic in `OrderService.buildItem`. Any future rule will need to be duplicated or will drift.

2. Product discount fields are not applied

   `CreateProductRequest` and `UpdateProductRequest` expose discount fields, but `ProductService.addProduct` does not set them during create. Update uses MapStruct, so update can persist them, but pricing still ignores them.

3. Snapshot data is incomplete

   `OrderItem` has `unitPrice`, `salePrice`, `discountAmount`, `discountPercentage`, and `subtotal`, but order creation only stores `salePrice` and `subtotal`. This makes reporting difficult because the order does not explain why a discount happened.

4. Money types are inconsistent

   Cart and attributes use `BigDecimal`, but order/order-item use `Double`. Discount logic should use `BigDecimal` internally to avoid rounding issues, even if existing DB columns remain `Double` during a first-pass refactor.

5. Coupon code is only a string

   `discountCode` is stored on order, but there is no `DiscountCode`, `Promotion`, or `Coupon` aggregate to validate active window, usage limit, minimum order, scope, or stacking.

## Recommended Target Model

Use a clear separation between discount definition, pricing calculation, and order snapshot.

### 1. Discount Definition

Keep the current simple fields as a first step, but treat them as definitions:

- Product campaign discount:
  - `Product.discountPercent`
  - `Product.discountStartDate`
  - `Product.discountEndDate`
- Variant override price:
  - `Attributes.salePrice`
- Future coupon:
  - `DiscountCode` entity or `Promotion` entity should be introduced only when code validation is implemented.

Rule precedence should be explicit:

1. If `Attributes.salePrice` is present, use it as the strongest variant-level override.
2. Else if product discount is active, calculate `price * (100 - discountPercent) / 100`.
3. Else use base `Attributes.price`.

This keeps existing behavior compatible while making product discount fields meaningful.

### 2. Pricing Calculation Boundary

Introduce a small domain service:

```java
@Service
public class DiscountPricingService {
    public PriceSnapshot price(Attributes attributes, LocalDateTime at) {
        // returns base price, sale price, discount amount, discount percent, source
    }
}
```

Suggested DTO/value object:

```java
public record PriceSnapshot(
        BigDecimal unitPrice,
        BigDecimal salePrice,
        BigDecimal discountAmount,
        BigDecimal discountPercent,
        String discountSource
) {}
```

Cart and order should both call this service. Entities should not own rule logic beyond simple derived setters.

### 3. Cart Representation

`ShoppingCart` can keep aggregate totals:

- `totalItems`
- `totalPrice`: sum of base price * quantity
- `totalSalePrice`: sum of effective sale price * quantity
- `totalDiscount`: derived as `totalPrice - totalSalePrice`

Do not persist per-item discount snapshots in cart yet unless the UI needs to display line-level discount details. Cart is mutable; order is the right place for immutable snapshot.

### 4. Order Snapshot

When creating `OrderItem`, store immutable pricing details:

- `unitPrice`: base price at checkout time
- `salePrice`: payable unit price at checkout time
- `discountAmount`: line discount amount, ideally `(unitPrice - salePrice) * quantity`
- `discountPercentage`: applied percent if percent-based, otherwise nullable or `0`
- `subtotal`: `salePrice * quantity`

Then calculate order totals from order items:

- `subtotal`: sum of item `unitPrice * quantity`, or rename current meaning if keeping backward compatibility.
- `discountAmount`: sum of item discounts plus valid order-level coupon discount.
- `totalAmount`: `subtotal - discountAmount + taxAmount + shippingFee`

If the existing API expects `subtotal` to mean payable item total, do not change it silently. Add fields such as `itemsOriginalAmount` and `itemsSaleAmount` later.

## Minimal Refactor Plan

### Phase 1: Unify calculation without schema changes

1. Add `DiscountPricingService` and `PriceSnapshot`.
2. Move `Helper.effectiveSalePrice` logic into that service.
3. Update `ShoppingCartService`/`Helper.recalculateAndUpdateTotals` to use `PriceSnapshot`.
4. Update `OrderService.buildItem` to use `PriceSnapshot` and populate `OrderItem.discountAmount` and `discountPercentage`.
5. Update `ProductService.addProduct` to persist product discount fields from `CreateProductRequest`.
6. Add validation:
   - `discountPercent` must be between `0` and `100`.
   - `discountEndDate` must be after `discountStartDate` when both are present.
   - `salePrice` should not be negative.

### Phase 2: Clarify coupon structure

Only after Phase 1 is stable, introduce an entity for order-level discounts:

- `DiscountCode`
- `DiscountType`: `PERCENT`, `FIXED_AMOUNT`, `FREE_SHIPPING`
- active window
- usage limit
- minimum order amount
- max discount amount
- scope: product/category/order

Then `CreateOrderRequest.discountCode` should be validated and converted into an order-level discount snapshot.

### Phase 3: Improve money model

Migrate order/order-item monetary fields from `Double` to `BigDecimal` in code and DB migration. This should be separate because it affects API serialization, tests, and persistence.

## Tests To Add

- Cart uses `Attributes.salePrice` when present.
- Cart uses active `Product.discountPercent` when `Attributes.salePrice` is null.
- Cart ignores expired/not-yet-started product discount.
- Order item snapshots discount amount and percent at checkout time.
- Changing product discount after checkout does not change existing order totals.
- Invalid discount percent/date range is rejected.
- `CreateProductRequest` discount fields are actually persisted.

## Suggested First Code Change

Start with Phase 1. It is the least risky because it does not require DB migration and keeps existing `salePrice` behavior as priority.

The key decision before implementation is naming:

- If `Attributes.salePrice` means manually overridden payable price, keep it.
- If `Product.discountPercent` should be the primary promotion mechanism, then `salePrice` should be treated as an override and documented as higher priority.

Recommended default: keep `Attributes.salePrice` higher priority to avoid breaking current cart behavior.
