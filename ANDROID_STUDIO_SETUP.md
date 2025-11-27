# Android Studio Setup Guide

## ✅ Installation Complete!

Android Studio has been successfully installed at:
```
/Applications/Android Studio.app
```

## 🚀 Next Steps

### 1. Launch Android Studio

You can launch it in two ways:

**Option A: From Applications**
```bash
open -a "Android Studio"
```

**Option B: From Spotlight**
- Press `Cmd + Space`
- Type "Android Studio"
- Press Enter

**Option C: From Terminal**
```bash
studio
```

### 2. First-Time Setup Wizard

When you first launch Android Studio, you'll see a setup wizard:

1. **Welcome Screen**
   - Click "Next"

2. **Install Type**
   - Choose "Standard" (recommended)
   - Click "Next"

3. **UI Theme**
   - Choose "Darcula" (dark theme) or "Light"
   - Click "Next"

4. **SDK Components**
   - The wizard will download:
     - Android SDK
     - Android SDK Platform
     - Android Virtual Device (AVD)
   - This will take 5-10 minutes
   - Click "Finish"

5. **Download Components**
   - Wait for all components to download
   - Click "Finish" when complete

### 3. Open the Sappho Project

1. On the Welcome screen, click **"Open"**
2. Navigate to: `/Users/mondo/Documents/git/sapphoapp`
3. Click "Open"

4. **First Project Open**
   - Gradle will sync automatically (this takes a few minutes)
   - Android Studio will index the project
   - Wait for "Gradle sync finished" notification

### 4. Configure Android SDK (if needed)

If prompted to install SDK components:

1. Go to: **Android Studio → Settings** (or `Cmd + ,`)
2. Navigate to: **Appearance & Behavior → System Settings → Android SDK**
3. Ensure these are checked:
   - ✅ Android 14.0 (API 34)
   - ✅ Android SDK Platform 34
   - ✅ Android SDK Build-Tools
4. Click "Apply" and "OK"

### 5. Create an Android Virtual Device (Emulator)

1. Go to: **Tools → Device Manager**
2. Click **"Create Device"**
3. Select a device:
   - Choose "Pixel 6" or "Pixel 7"
   - Click "Next"
4. Select a system image:
   - Choose "UpsideDownCake" (API 34, Android 14)
   - Click "Download" if not already downloaded
   - Click "Next"
5. Configure AVD:
   - Name: "Pixel_6_API_34" (or keep default)
   - Click "Finish"

### 6. Run the Sappho App

1. **Select Device**: Click the device dropdown (top toolbar)
   - Select your emulator (e.g., "Pixel_6_API_34")

2. **Run**: Click the green play button ▶️ or press `Shift + F10`
   - The emulator will launch (first launch takes 1-2 minutes)
   - The app will install and launch automatically

3. **Configure Server**:
   - Enter your Sappho server URL
   - For local server: `http://10.0.2.2:3002` (emulator special IP)
   - For network server: `http://192.168.1.100:3002` (your actual IP)

4. **Login**: Use your Sappho credentials

## 🔧 Useful Shortcuts

- `Cmd + ,` - Settings
- `Shift + F10` - Run app
- `Ctrl + R` - Run app
- `Cmd + F9` - Build project
- `Shift + Shift` - Search everywhere

## 🐛 Troubleshooting

### Gradle Sync Failed
```bash
# In Android Studio: File → Invalidate Caches / Restart
```

### SDK Not Found
1. Settings → Android SDK
2. Click "Edit" next to SDK Location
3. Let Android Studio download the SDK

### Emulator Won't Start
1. Tools → Device Manager
2. Delete the AVD
3. Create a new one

### Can't Connect to Server
- For emulator: Use `10.0.2.2` instead of `localhost`
- For physical device: Make sure device and server are on same WiFi
- Check that Sappho server is running: `http://your-server-ip:3002`

## 📱 Testing on Physical Device

### Setup
1. **Enable Developer Options** on your Android phone:
   - Settings → About Phone
   - Tap "Build Number" 7 times

2. **Enable USB Debugging**:
   - Settings → System → Developer Options
   - Turn on "USB Debugging"

3. **Connect via USB**:
   - Plug in your phone
   - Allow USB debugging when prompted
   - Your device will appear in the device dropdown

### Run
- Select your physical device from dropdown
- Click run ▶️
- Use your actual server IP (not 10.0.2.2)

## 🎯 Quick Start Command

To open the project from terminal:
```bash
cd /Users/mondo/Documents/git/sapphoapp
open -a "Android Studio" .
```

## 📚 Resources

- [Android Studio User Guide](https://developer.android.com/studio/intro)
- [Jetpack Compose Tutorial](https://developer.android.com/jetpack/compose/tutorial)
- [Android Emulator Guide](https://developer.android.com/studio/run/emulator)

---

**You're all set! Happy coding! 🎧**
