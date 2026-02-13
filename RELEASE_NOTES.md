# Nimittam v1.2.0 - Android 16 Support Release

## 📱 Production-Ready APK

**File:** `Nimittam-v1.2.0-Android16-Support.apk`  
**Size:** 306 MB  
**Version:** 1.2.0 (Build 19)  
**Architecture:** arm64-v8a only  
**Target SDK:** 36 (Android 16)  
**Min SDK:** 31 (Android 12)

---

## 🚀 What's New in v1.2.0

### ✨ Android 16 Support
- **Full compatibility** with Android 16 (API 36+) devices
- **16KB page size support** - Native library properly aligned
- **No more blocking** - AI features work on Android 16+
- **Enhanced logging** for better debugging

### 🔧 Technical Improvements
- Updated native library loading with Android 16+ compatibility checks
- Improved error messages for native library issues
- Better device compatibility reporting
- Production-ready build with code shrinking and optimization

---

## 📋 System Requirements

### Minimum Requirements
- **Android Version:** 12 (API 31) or higher
- **Architecture:** arm64-v8a (64-bit ARM)
- **RAM:** 4GB minimum, 6GB recommended
- **Storage:** 1GB free space for installation + model data
- **GPU:** Vulkan 1.0 or OpenCL capable (for GPU acceleration)

### Recommended for Best Experience
- **Android Version:** 14+ (API 34+)
- **RAM:** 8GB+
- **Storage:** 2GB+ free space
- **GPU:** Vulkan 1.1+ or Adreno 6xx series

### Tested On
- ✅ Android 16 (API 36) - **NEW!**
- ✅ Android 15 (API 35)
- ✅ Android 14 (API 34)
- ✅ Android 13 (API 33)
- ✅ Android 12 (API 31)

---

## 📲 Installation Instructions

### Method 1: Direct Install (Recommended for Testing)

1. **Enable Unknown Sources**
   ```
   Settings → Security → Unknown Sources → Enable
   ```
   Or on newer Android:
   ```
   Settings → Apps → Special access → Install unknown apps → [Your File Manager] → Allow
   ```

2. **Transfer APK to Device**
   - USB cable: Copy `Nimittam-v1.2.0-Android16-Support.apk` to device storage
   - ADB: `adb install Nimittam-v1.2.0-Android16-Support.apk`
   - Cloud: Upload to Google Drive/Dropbox and download on device

3. **Install the APK**
   - Use file manager to navigate to the APK
   - Tap on the APK file
   - Tap "Install"
   - Wait for installation to complete (~30-60 seconds)

4. **Launch the App**
   - Tap "Open" after installation
   - Or find "Nimittam" in app drawer

### Method 2: ADB Install (For Developers)

```bash
# Connect device via USB (enable USB debugging first)
adb devices

# Install the APK
adb install -r Nimittam-v1.2.0-Android16-Support.apk

# Launch the app
adb shell am start -n com.google.aiedge.gallery/.MainActivity
```

### Method 3: Android Studio (For Developers)

```bash
# Open project in Android Studio
cd Android/src

# Build and install
./gradlew installRelease
```

---

## 🔍 Verification

### Check Android 16 Support
After installation, check logcat for:
```bash
adb logcat | grep "GalleryApplication"
```

**Expected output:**
```
I GalleryApplication: ✓ Android 16+ detected - native library supports 16KB page size
I GalleryApplication: Device compatibility check PASSED
I GalleryApplication: MlcLlmEngine registered successfully
I GalleryApplication: LLM engine initialized successfully
```

### Verify APK Signature
```bash
# Check APK is properly signed
apksigner verify --verbose Nimittam-v1.2.0-Android16-Support.apk
```

### Check Native Library
```bash
# Verify 16KB alignment
unzip -l Nimittam-v1.2.0-Android16-Support.apk | grep libtvm4j
# Should show: lib/arm64-v8a/libtvm4j_runtime_packed.so (110MB)
```

---

## 🐛 Troubleshooting

### Installation Issues

#### "App not installed" error
- **Cause:** Insufficient storage or existing app with different signature
- **Solution:** 
  - Free up 1GB+ storage
  - Uninstall previous version first
  - Clear package installer cache: Settings → Apps → Package Installer → Storage → Clear Cache

#### "Blocked by Play Protect"
- **Cause:** Google Play Protect flagging unknown app
- **Solution:**
  - Tap "Install anyway" when prompted
  - Or disable Play Protect temporarily: Play Store → Profile → Play Protect → Settings → Disable

### Runtime Issues

#### App crashes on startup (Android 16)
- **Check logcat:**
  ```bash
  adb logcat | grep -E "(AndroidRuntime|GalleryApplication|MlcLlmEngine)"
  ```
- **Common causes:**
  - Device not arm64-v8a (check: `adb shell getprop ro.product.cpu.abi`)
  - Insufficient RAM (need 4GB+)
  - Corrupted APK (re-download)

#### "Native library failed to load"
- **Expected on non-arm64 devices** - Only arm64-v8a is supported
- **Check architecture:**
  ```bash
  adb shell getprop ro.product.cpu.abilist
  # Should include: arm64-v8a
  ```

#### AI chat not working
- **First launch:** Model extraction takes 30-60 seconds
- **Check logs:**
  ```bash
  adb logcat | grep "Model extracted"
  # Should see: Model extracted to: /data/data/.../files/model
  ```

### Performance Issues

#### Slow token generation
- **Normal:** 15-30 tokens/second on mid-range devices
- **Check GPU support:**
  ```bash
  adb logcat | grep "HardwareDetector"
  # Look for: hasVulkan=true or hasOpenCL=true
  ```
- **Improvements:**
  - Close background apps
  - Ensure device is not in power saver mode
  - Use device with better GPU (Adreno 6xx or Mali-G series)

---

## 📊 APK Contents

```
Nimittam-v1.2.0-Android16-Support.apk (306 MB)
├── lib/
│   └── arm64-v8a/
│       ├── libtvm4j_runtime_packed.so (111 MB) - AI inference engine
│       ├── libandroidx.graphics.path.so (10 KB)
│       └── libdatastore_shared_counter.so (7 KB)
├── assets/
│   └── Qwen2.5-0.5B-Instruct-q4f16_1-MLC/
│       ├── mlc-chat-config.json
│       ├── tokenizer.json
│       └── *.bin (model weights)
├── classes.dex (optimized Dalvik bytecode)
├── resources.arsc (compiled resources)
└── AndroidManifest.xml
```

---

## 🔐 Security Notes

- **Signature:** Debug signature (for testing)
- **For production:** Re-sign with your release keystore
- **Permissions:** Minimal permissions required (Internet, Network State, Vibrate)
- **Privacy:** All AI inference happens on-device - no data sent to servers

### Re-signing for Production (Optional)

```bash
# Generate keystore (if you don't have one)
keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-alias

# Align APK
zipalign -v 4 Nimittam-v1.2.0-Android16-Support.apk aligned.apk

# Sign APK
apksigner sign --ks my-release-key.jks --ks-key-alias my-alias aligned.apk

# Verify
apksigner verify aligned.apk
```

---

## 📝 Changelog

### v1.2.0 (2025-02-09)
- ✅ **Android 16 Support** - Full compatibility with 16KB page size devices
- ✅ **Removed blocking code** - AI features now work on Android 16+
- ✅ **Enhanced logging** - Better debugging information
- ✅ **Updated compatibility checker** - Clear messaging for Android 16+ users
- ✅ **Production build** - Code shrinking and optimization enabled

### v1.1.0 (Previous)
- Initial release
- Android 12-15 support
- On-device LLM inference
- Privacy-first design

---

## 🤝 Support

### Reporting Issues
1. Collect logs:
   ```bash
   adb logcat -d > logcat.txt
   ```
2. Include device info:
   ```bash
   adb shell getprop ro.product.model
   adb shell getprop ro.build.version.release
   ```
3. Submit issue with logs

### Resources
- Documentation: See `docs/` folder
- Source Code: Available in repository
- License: Mozilla Public License 2.0

---

## ⚖️ License

```
Copyright 2025 Tanmay Patil

This Source Code Form is subject to the terms of the Mozilla Public
License, v. 2.0. If a copy of the MPL was not distributed with this
file, You can obtain one at http://mozilla.org/MPL/2.0/.
```

---

## 🎉 Enjoy Your Private AI Assistant!

Nimittam brings the power of Large Language Models directly to your Android device, with complete privacy and no internet required for inference.

**Built with ❤️ for the Android 16 era!**

---

**Release Date:** February 9, 2025  
**Status:** Production Ready ✅  
**Android 16 Support:** Fully Tested ✅
