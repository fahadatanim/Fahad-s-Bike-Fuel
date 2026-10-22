# Fahad's Bike Meter & Fuel

Offline-first Android app source project for a bike with a broken odometer.

## Features
- Manual trip distance entry when the phone was not carried.
- GPS trip tracking while the phone is carried (foreground location service).
- Odometer baseline and cumulative distance.
- Fuel entries by litres, money, or both.
- Estimated remaining fuel, average km/litre, and fuel cost per km.
- Local storage using SharedPreferences; no account or internet needed.

## Build APK
Open this folder in Android Studio (recent version), allow Gradle sync, then choose:
**Build > Build Bundle(s) / APK(s) > Build APK(s)**.
APK output is normally `app/build/outputs/apk/debug/app-debug.apk`.

This environment did not have Android SDK/Gradle installed, so an APK could not be compiled here. The complete Android source project is provided.

## Notes
- GPS distance is an estimate and can be inaccurate in dense areas or when GPS signal is poor.
- For best fuel estimates, start by recording the current fuel amount, then add every refill.
- “Remaining fuel” is a bookkeeping estimate, not a sensor reading from the motorcycle.
