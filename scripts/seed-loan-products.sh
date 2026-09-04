#!/usr/bin/env bash
# =============================================================================
# Seed Fake Loan Products into Apache Fineract
# Usage: ./scripts/seed-loan-products.sh [--base-url URL] [--tenant TENANT]
# =============================================================================
set -euo pipefail

FINERACT_BASE_URL="${FINERACT_BASE_URL:-https://localhost:8443/fineract-provider/api/v1}"
FINERACT_TENANT="${FINERACT_TENANT:-default}"
FINERACT_USERNAME="${FINERACT_USERNAME:-mifos}"
FINERACT_PASSWORD="${FINERACT_PASSWORD:-password}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url) FINERACT_BASE_URL="$2"; shift 2 ;;
    --tenant)   FINERACT_TENANT="$2";   shift 2 ;;
    --username) FINERACT_USERNAME="$2"; shift 2 ;;
    --password) FINERACT_PASSWORD="$2"; shift 2 ;;
    *) echo "Unknown flag: $1"; exit 1 ;;
  esac
done

AUTH_HEADER="Authorization: Basic $(echo -n "${FINERACT_USERNAME}:${FINERACT_PASSWORD}" | base64)"
TENANT_HEADER="Fineract-Platform-TenantId: ${FINERACT_TENANT}"
BASE="${FINERACT_BASE_URL}"

echo "==> Fineract: ${BASE}"
echo "==> Tenant  : ${FINERACT_TENANT}"
echo ""

create_loan_product() {
  local label="$1"
  local payload="$2"
  echo -n "  Creating [${label}] ... "
  response=$(curl -sk \
    -X POST "${BASE}/loanproducts" \
    -H "${AUTH_HEADER}" \
    -H "${TENANT_HEADER}" \
    -H "Content-Type: application/json" \
    -d "${payload}")
  resource_id=$(echo "${response}" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('resourceId',''))" 2>/dev/null || true)
  if [[ -n "${resource_id}" ]]; then
    echo "OK  (id=${resource_id})"
  else
    echo "FAIL"
    echo "     Response: ${response}"
  fi
}

# Shared required fields for all products:
#   daysInYearType: 1 (Actual)
#   daysInMonthType: 1 (Actual)
#   isInterestRecalculationEnabled: false
#   shortName: max 4 chars

# ============================================================================
# 1. Vay Tieu Dung Ca Nhan  |  5tr-100tr  |  1.5%/thang  |  6-36 thang
# ============================================================================
create_loan_product "Vay Tieu Dung Ca Nhan" '{
  "name": "Vay Tieu Dung Ca Nhan",
  "shortName": "TDCN",
  "description": "Goi vay tieu dung danh cho ca nhan, khong can tai san dam bao. Lai suat canh tranh, giai ngan nhanh trong 24h.",
  "currencyCode": "VND",
  "digitsAfterDecimal": 0,
  "inMultiplesOf": 1000,
  "principal": 30000000,
  "minPrincipal": 5000000,
  "maxPrincipal": 100000000,
  "numberOfRepayments": 12,
  "minNumberOfRepayments": 6,
  "maxNumberOfRepayments": 36,
  "repaymentEvery": 1,
  "repaymentFrequencyType": 2,
  "interestRatePerPeriod": 1.5,
  "interestRateFrequencyType": 2,
  "minInterestRatePerPeriod": 1.0,
  "maxInterestRatePerPeriod": 2.0,
  "amortizationType": 1,
  "interestType": 0,
  "interestCalculationPeriodType": 1,
  "daysInMonthType": 1,
  "daysInYearType": 1,
  "isInterestRecalculationEnabled": false,
  "transactionProcessingStrategyCode": "mifos-standard-strategy",
  "accountingRule": 1,
  "dateFormat": "dd MMMM yyyy",
  "locale": "en",
  "includeInBorrowerCycle": false,
  "useBorrowerCycle": false,
  "isLinkedToFloatingInterestRates": false,
  "allowVariableInstallments": false,
  "canDefineInstallmentAmount": false,
  "canUseForTopup": false
}'

# ============================================================================
# 2. Vay Kinh Doanh Nho  |  50tr-500tr  |  1.2%/thang  |  12-60 thang
# ============================================================================
create_loan_product "Vay Kinh Doanh Nho" '{
  "name": "Vay Kinh Doanh Nho",
  "shortName": "VKDN",
  "description": "Goi vay ho tro ho kinh doanh va doanh nghiep nho. Tai san dam bao linh hoat. Uu dai lai suat nam dau.",
  "currencyCode": "VND",
  "digitsAfterDecimal": 0,
  "inMultiplesOf": 1000,
  "principal": 200000000,
  "minPrincipal": 50000000,
  "maxPrincipal": 500000000,
  "numberOfRepayments": 24,
  "minNumberOfRepayments": 12,
  "maxNumberOfRepayments": 60,
  "repaymentEvery": 1,
  "repaymentFrequencyType": 2,
  "interestRatePerPeriod": 1.2,
  "interestRateFrequencyType": 2,
  "minInterestRatePerPeriod": 0.9,
  "maxInterestRatePerPeriod": 1.5,
  "amortizationType": 1,
  "interestType": 0,
  "interestCalculationPeriodType": 1,
  "daysInMonthType": 1,
  "daysInYearType": 1,
  "isInterestRecalculationEnabled": false,
  "transactionProcessingStrategyCode": "mifos-standard-strategy",
  "accountingRule": 1,
  "dateFormat": "dd MMMM yyyy",
  "locale": "en",
  "includeInBorrowerCycle": false,
  "useBorrowerCycle": false,
  "isLinkedToFloatingInterestRates": false,
  "allowVariableInstallments": false,
  "canDefineInstallmentAmount": false,
  "canUseForTopup": false
}'

# ============================================================================
# 3. Vay Mua Nha The Chap  |  500tr-5ty  |  0.8%/thang  |  60-240 thang
# ============================================================================
create_loan_product "Vay Mua Nha The Chap" '{
  "name": "Vay Mua Nha The Chap",
  "shortName": "MNTC",
  "description": "Goi vay mua nha dai han, the chap bat dong san. Lai suat uu dai co dinh 2 nam dau. Giai ngan theo tien do thi cong.",
  "currencyCode": "VND",
  "digitsAfterDecimal": 0,
  "inMultiplesOf": 1000,
  "principal": 1000000000,
  "minPrincipal": 500000000,
  "maxPrincipal": 5000000000,
  "numberOfRepayments": 120,
  "minNumberOfRepayments": 60,
  "maxNumberOfRepayments": 240,
  "repaymentEvery": 1,
  "repaymentFrequencyType": 2,
  "interestRatePerPeriod": 0.8,
  "interestRateFrequencyType": 2,
  "minInterestRatePerPeriod": 0.6,
  "maxInterestRatePerPeriod": 1.0,
  "amortizationType": 1,
  "interestType": 0,
  "interestCalculationPeriodType": 1,
  "daysInMonthType": 1,
  "daysInYearType": 1,
  "isInterestRecalculationEnabled": false,
  "transactionProcessingStrategyCode": "mifos-standard-strategy",
  "accountingRule": 1,
  "dateFormat": "dd MMMM yyyy",
  "locale": "en",
  "includeInBorrowerCycle": false,
  "useBorrowerCycle": false,
  "isLinkedToFloatingInterestRates": false,
  "allowVariableInstallments": false,
  "canDefineInstallmentAmount": false,
  "canUseForTopup": false
}'

# ============================================================================
# 4. Vay Tin Chap Nhanh  |  1tr-30tr  |  2.5%/thang  |  1-12 thang
# ============================================================================
create_loan_product "Vay Tin Chap Nhanh" '{
  "name": "Vay Tin Chap Nhanh",
  "shortName": "VTCN",
  "description": "Vay tin chap sieu toc, khong can tai san dam bao. Phe duyet trong 2 gio, giai ngan trong ngay lam viec. Phu hop chi tieu khan cap.",
  "currencyCode": "VND",
  "digitsAfterDecimal": 0,
  "inMultiplesOf": 500,
  "principal": 10000000,
  "minPrincipal": 1000000,
  "maxPrincipal": 30000000,
  "numberOfRepayments": 6,
  "minNumberOfRepayments": 1,
  "maxNumberOfRepayments": 12,
  "repaymentEvery": 1,
  "repaymentFrequencyType": 2,
  "interestRatePerPeriod": 2.5,
  "interestRateFrequencyType": 2,
  "minInterestRatePerPeriod": 2.0,
  "maxInterestRatePerPeriod": 3.0,
  "amortizationType": 1,
  "interestType": 0,
  "interestCalculationPeriodType": 1,
  "daysInMonthType": 1,
  "daysInYearType": 1,
  "isInterestRecalculationEnabled": false,
  "transactionProcessingStrategyCode": "mifos-standard-strategy",
  "accountingRule": 1,
  "dateFormat": "dd MMMM yyyy",
  "locale": "en",
  "includeInBorrowerCycle": false,
  "useBorrowerCycle": false,
  "isLinkedToFloatingInterestRates": false,
  "allowVariableInstallments": false,
  "canDefineInstallmentAmount": false,
  "canUseForTopup": false
}'

# ============================================================================
# 5. Vay Doanh Nghiep Vua  |  1ty-20ty  |  0.9%/thang  |  12-84 thang
# ============================================================================
create_loan_product "Vay Doanh Nghiep Vua" '{
  "name": "Vay Doanh Nghiep Vua",
  "shortName": "VDNV",
  "description": "Goi vay danh cho doanh nghiep vua co doanh thu hang nam 10-200 ty. Can bao cao tai chinh 2 nam gan nhat. Lai suat linh hoat theo thi truong.",
  "currencyCode": "VND",
  "digitsAfterDecimal": 0,
  "inMultiplesOf": 1000,
  "principal": 5000000000,
  "minPrincipal": 1000000000,
  "maxPrincipal": 20000000000,
  "numberOfRepayments": 36,
  "minNumberOfRepayments": 12,
  "maxNumberOfRepayments": 84,
  "repaymentEvery": 1,
  "repaymentFrequencyType": 2,
  "interestRatePerPeriod": 0.9,
  "interestRateFrequencyType": 2,
  "minInterestRatePerPeriod": 0.7,
  "maxInterestRatePerPeriod": 1.2,
  "amortizationType": 1,
  "interestType": 0,
  "interestCalculationPeriodType": 1,
  "daysInMonthType": 1,
  "daysInYearType": 1,
  "isInterestRecalculationEnabled": false,
  "transactionProcessingStrategyCode": "mifos-standard-strategy",
  "accountingRule": 1,
  "dateFormat": "dd MMMM yyyy",
  "locale": "en",
  "includeInBorrowerCycle": false,
  "useBorrowerCycle": false,
  "isLinkedToFloatingInterestRates": false,
  "allowVariableInstallments": false,
  "canDefineInstallmentAmount": false,
  "canUseForTopup": false
}'

# ---- Verify ----------------------------------------------------------------
echo ""
echo "==> Verifying created products..."
echo ""

curl -sk \
  -H "${AUTH_HEADER}" \
  -H "${TENANT_HEADER}" \
  "${BASE}/loanproducts" \
  | python3 -c "
import sys, json
products = json.load(sys.stdin)
print(f'  Total loan products: {len(products)}')
for p in products:
    min_p = p.get('minPrincipal', 0)
    max_p = p.get('maxPrincipal', 0)
    ccy   = p['currency']['code']
    sname = p.get('shortName', '')
    print(f'  [{p[\"id\"]:>3}] {p[\"name\"]:<30} ({sname:<4})  {ccy}  {min_p:>15,} – {max_p:>15,}')
"
