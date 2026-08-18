#!/usr/bin/env bash
set -e

# ==============================================================================
# Script: nlm-sync.sh
# Hỗ trợ đồng bộ tài liệu vào hệ thống Đa Sổ (Multi-Notebooks)
# Cách dùng:
#   ./scripts/nlm-sync.sh [target_notebook] [target_path]
# Ví dụ:
#   ./scripts/nlm-sync.sh erp-arch docs/ONBOARDING.md
#   ./scripts/nlm-sync.sh erp-api docs/API_DOCUMENTATION.md
#   ./scripts/nlm-sync.sh erp-fineract docs/FINERACT_INTEGRATION_GUIDE.md
#   ./scripts/nlm-sync.sh all
# ==============================================================================

TARGET_NOTEBOOK="${1:-erp-arch}"
TARGET_PATH="${2:-docs}"

sync_notebook() {
    local nb="$1"
    local path="$2"
    echo "🚀 Bắt đầu đồng bộ vào Notebook: $nb (Path: $path)..."
    if [ -f "$path" ]; then
        echo "  📄 Uploading file: $path"
        nlm source add "$nb" --file "$path" --wait --json
    elif [ -d "$path" ]; then
        find "$path" -name "*.md" | while read -r file; do
            echo "  ➜ Uploading: $file"
            nlm source add "$nb" --file "$file" --wait --json
        done
    fi
}

if [ "$TARGET_NOTEBOOK" = "all" ]; then
    echo "🌟 Đồng bộ toàn diện vào cả 3 sổ trong hệ thống..."
    sync_notebook "erp-arch" "docs/ONBOARDING.md"
    sync_notebook "erp-arch" "CLAUDE.md"
    sync_notebook "erp-arch" "README.md"
    sync_notebook "erp-arch" "docs/decisions"
    sync_notebook "erp-api" "docs/API_DOCUMENTATION.md"
    sync_notebook "erp-fineract" "docs/FINERACT_INTEGRATION_GUIDE.md"
    sync_notebook "erp-fineract" "docs/decisions/ADR-005-apache-fineract-ledger-integration.md"
    echo "✅ Toàn bộ 3 sổ đã được đồng bộ hoàn tất!"
    exit 0
fi

if [ -z "$2" ] && [ -f "$1" ]; then
    TARGET_PATH="$1"
    TARGET_NOTEBOOK="erp-arch"
fi

sync_notebook "$TARGET_NOTEBOOK" "$TARGET_PATH"
echo "✅ Đồng bộ hoàn tất!"
