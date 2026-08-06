# Worker Task: Implement Iteration 2 Fixes for Shopping Cart

## Context
The Shopping Cart feature was implemented in Iteration 1 but failed the Gate due to 4 logic flaws. Your task is to apply the synthesized fix strategy from the Explorers.

## Implementation Steps

### 1. Fix Integer Overflow
**File:** `src/main/java/com/anno/ERP_SpringBoot_Experiment/model/entity/ShoppingCart.java`
**Action:** In `addItem(String sku, int quantity)`, use `Math.addExact()` to update the quantity, and throw a `BusinessException(ErrorCode.BAD_REQUEST)` or `IllegalArgumentException` if the resulting quantity exceeds a reasonable limit (e.g., 9999). Apply the same check when adding a brand new item.

### 2. Fix Math.abs() Edge Case
**File:** `src/main/java/com/anno/ERP_SpringBoot_Experiment/service/Merchandise/ShoppingCartService.java`
**Action:** In the `add` method, replace `Math.abs(quantity)` with `-quantity`. Since `quantity` is bounded to `> -9999` locally, `-quantity` will safely be positive and avoid the `Integer.MIN_VALUE` issue of `Math.abs`.

### 3. Fix Sale Price Bug
**File:** `src/main/java/com/anno/ERP_SpringBoot_Experiment/service/Merchandise/Helper.java`
**Action:** In `recalculateAndUpdateTotals`, change the sale price evaluation logic to:
`Double sp = a.getSalePrice();`
`(sp != null && sp > 0) ? sp : a.getPrice()`
This prevents treating `0.0` or `null` as free items.

### 4. Fix Lazy Cart Initialization Transaction Tainting
**File:** `src/main/java/com/anno/ERP_SpringBoot_Experiment/service/Merchandise/Helper.java` and `ShoppingCartService.java`
**Action:** 
1. Inject `ShoppingCartRepository` into `Helper.java`.
2. Create a new method in `Helper.java`:
```java
@org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
public ShoppingCart getOrCreateCart(User user, com.ddicg.erp.repository.ShoppingCartRepository repo) {
    return repo.findByUser(user).orElseGet(() -> {
        try {
            return repo.saveAndFlush(createNewCart(user));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return repo.findByUser(user).orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "Error creating cart"));
        }
    });
}
```
3. In `ShoppingCartService.java`, replace the existing cart initialization block (the `findByUser` + `saveAndFlush` with `try/catch` logic inside `add` and `getCart`) with a simple call to `helper.getOrCreateCart(user, shoppingCartRepository)`.

## MANDATORY INTEGRITY WARNING
> DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A Forensic Auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

## Acceptance
- The project must compile successfully (`./mvnw clean compile -q`).
- Write your final handoff report to `/home/ddicgegd/Projects/erp_springboot-experiment/.agents/worker_cart_2/handoff.md` summarizing the changes made.
