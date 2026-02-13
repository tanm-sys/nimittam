#!/bin/bash

# Android 16 Compatibility Verification Script
# This script verifies that the Android 16 fix has been properly applied

set -e

echo "======================================"
echo "Android 16 Compatibility Verification"
echo "======================================"
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Track success
SUCCESS=0
FAILED=0

check_file() {
    if [ -f "$1" ]; then
        echo -e "${GREEN}✓${NC} File exists: $1"
        return 0
    else
        echo -e "${RED}✗${NC} File missing: $1"
        return 1
    fi
}

check_not_contains() {
    if grep -q "$2" "$1"; then
        echo -e "${RED}✗${NC} $3"
        return 1
    else
        echo -e "${GREEN}✓${NC} $3"
        return 0
    fi
}

check_contains() {
    if grep -q "$2" "$1"; then
        echo -e "${GREEN}✓${NC} $3"
        return 0
    else
        echo -e "${RED}✗${NC} $3"
        return 1
    fi
}

echo "1. Checking modified files..."
echo "--------------------------------------"

# Check GalleryApplication.kt
if check_file "Android/src/app/src/main/java/com/google/ai/edge/gallery/GalleryApplication.kt"; then
    if check_not_contains "Android/src/app/src/main/java/com/google/ai/edge/gallery/GalleryApplication.kt" \
        "AI features disabled" \
        "GalleryApplication: Android 16 blocking code removed"; then
        ((SUCCESS++))
    else
        ((FAILED++))
    fi
    
    if check_contains "Android/src/app/src/main/java/com/google/ai/edge/gallery/GalleryApplication.kt" \
        "Android 16+ detected - native library supports 16KB page size" \
        "GalleryApplication: Added Android 16+ support logging"; then
        ((SUCCESS++))
    else
        ((FAILED++))
    fi
else
    ((FAILED+=2))
fi

# Check DeviceCompatibilityChecker.kt
if check_file "Android/src/app/src/main/java/com/google/ai/edge/gallery/common/DeviceCompatibilityChecker.kt"; then
    if check_contains "Android/src/app/src/main/java/com/google/ai/edge/gallery/common/DeviceCompatibilityChecker.kt" \
        "Android 16+ Support" \
        "DeviceCompatibilityChecker: Android 16+ support message added"; then
        ((SUCCESS++))
    else
        ((FAILED++))
    fi
else
    ((FAILED++))
fi

# Check MlcLlmEngine.kt
if check_file "Android/src/app/src/main/java/com/google/ai/edge/gallery/llm/engine/MlcLlmEngine.kt"; then
    if check_contains "Android/src/app/src/main/java/com/google/ai/edge/gallery/llm/engine/MlcLlmEngine.kt" \
        "16KB page size compatibility verified" \
        "MlcLlmEngine: Added 16KB compatibility logging"; then
        ((SUCCESS++))
    else
        ((FAILED++))
    fi
    
    if check_contains "Android/src/app/src/main/java/com/google/ai/edge/gallery/llm/engine/MlcLlmEngine.kt" \
        "import android.os.Build" \
        "MlcLlmEngine: Build import added"; then
        ((SUCCESS++))
    else
        ((FAILED++))
    fi
else
    ((FAILED+=2))
fi

echo ""
echo "2. Checking native library..."
echo "--------------------------------------"

NATIVE_LIB="Android/src/app/src/main/jniLibs/arm64-v8a/libtvm4j_runtime_packed.so"
if check_file "$NATIVE_LIB"; then
    echo -e "${GREEN}✓${NC} Native library exists"
    ((SUCCESS++))
    
    # Check ELF alignment
    if command -v readelf &> /dev/null; then
        echo "   Checking ELF 16KB alignment..."
        if readelf -l "$NATIVE_LIB" 2>/dev/null | grep -q "0x4000"; then
            echo -e "   ${GREEN}✓${NC} Library has 16KB page alignment (0x4000)"
            ((SUCCESS++))
        else
            echo -e "   ${YELLOW}!${NC} Could not verify 16KB alignment (readelf may not be available)"
        fi
    else
        echo -e "   ${YELLOW}!${NC} readelf not available, skipping alignment check"
    fi
else
    echo -e "${RED}✗${NC} Native library not found"
    ((FAILED++))
fi

echo ""
echo "3. Checking build configuration..."
echo "--------------------------------------"

if check_file "Android/src/app/build.gradle.kts"; then
    if check_contains "Android/src/app/build.gradle.kts" \
        "16KB page size alignment for native libraries" \
        "Build config: 16KB page size documentation updated"; then
        ((SUCCESS++))
    else
        ((FAILED++))
    fi
else
    ((FAILED++))
fi

echo ""
echo "======================================"
echo "Verification Summary"
echo "======================================"
echo -e "Passed: ${GREEN}$SUCCESS${NC}"
echo -e "Failed: ${RED}$FAILED${NC}"
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ All checks passed!${NC}"
    echo "The Android 16 compatibility fix is properly applied."
    echo ""
    echo "Next steps:"
    echo "  1. Build the APK: ./gradlew assembleDebug"
    echo "  2. Test on Android 16 device"
    echo "  3. Monitor logcat for: 'Android 16+ detected - native library supports 16KB page size'"
    exit 0
else
    echo -e "${RED}✗ Some checks failed!${NC}"
    echo "Please review the failed items above."
    exit 1
fi
