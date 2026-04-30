# GasTech POS — Capacitor Native Build

تطبيق POS احترافي يجمع بين **React/TanStack** للواجهة و **Capacitor + Sunmi SDK** للوصول الكامل للـ Hardware.

---

## 🎯 ما الجديد في هذه النسخة (v7-Capacitor)

| الميزة | قبل (WebView) | بعد (Capacitor) |
|--------|---|---|
| طباعة Sunmi | عبر JS Bridge هش | **Sunmi SDK مباشرة** عبر Plugin Native |
| Bluetooth | Web Bluetooth (محدود) | **@capacitor-community/bluetooth-le** Native |
| GPS | navigator.geolocation | **@capacitor/geolocation** بصلاحيات Native |
| العربي | يطبع رموز | يُرسم Bitmap بخط Cairo (مثالي) |
| التحديثات | إعادة تثبيت | **فورية** (server.url يحمّل من Lovable) |

---

## 📋 المتطلبات

- Node.js 20+
- Java JDK 17
- Android Studio (أو Codemagic)
- Sunmi Printer SDK (مجاني من Sunmi Developer Portal)
- خطوط Cairo: [حمّلها من Google Fonts](https://fonts.google.com/specimen/Cairo)

---

## 🚀 الإعداد المحلي (مرة واحدة)

```bash
# 1) ثبّت حزم Capacitor
bun add @capacitor/core @capacitor/android @capacitor/geolocation \
        @capacitor/splash-screen @capacitor/status-bar @capacitor/app \
        @capacitor-community/bluetooth-le
bun add -d @capacitor/cli

# 2) أنشئ مشروع Android
npx cap add android

# 3) انسخ الإعدادات الجاهزة من هذا الـ ZIP
cp capacitor.config.ts ./
cp android-config/AndroidManifest.xml android/app/src/main/
cp android-config/MainActivity.java android/app/src/main/java/com/gastech/pos/

# 4) أنشئ Sunmi Plugin Module
mkdir -p android/capacitor-sunmi
cp -r plugin-sunmi/* android/capacitor-sunmi/

# 5) حمّل Sunmi AIDL files من:
#    https://file.cdn.sunmi.com/SUNMIDOCUMENT/PrinterSDK/PrinterSDK_v1.0.16.zip
#    وضعها في:
#    android/capacitor-sunmi/src/main/aidl/woyou/aidlservice/jiuiv5/

# 6) ضع خطوط Cairo في:
#    android/capacitor-sunmi/src/main/assets/fonts/Cairo-Regular.ttf
#    android/capacitor-sunmi/src/main/assets/fonts/Cairo-Bold.ttf

# 7) أضف الـ module في android/settings.gradle:
#    include ':capacitor-sunmi'
#    project(':capacitor-sunmi').projectDir = new File('./capacitor-sunmi')

# 8) أضف dependency في android/app/build.gradle:
#    implementation project(':capacitor-sunmi')

# 9) Sync و Build
bun run build
npx cap sync android
npx cap open android   # أو: cd android && ./gradlew assembleRelease
```

---

## 🌐 ملاحظة مهمة: Live Reload

الإعداد الحالي في `capacitor.config.ts`:

```ts
server: {
  url: 'https://gastech.lovable.app',
}
```

يعني التطبيق **يحمّل الموقع من Lovable مباشرة** — أي تعديل في الكود يظهر فوراً بدون إعادة بناء APK.

✅ **مزايا**: تحديثات فورية، لا حاجة لإعادة تثبيت
⚠️ **عيب**: يحتاج إنترنت دائماً (للـ first load — بعدها PWA cache يشتغل)

لو عايز Offline-first كامل، احذف `server.url` و سيتم تضمين `dist/` داخل الـ APK.

---

## 🖨️ اختبار الطباعة

1. ثبّت الـ APK على Sunmi V2
2. افتح التطبيق → سجّل الدخول
3. روح **Settings → Receipt**
4. لازم تشوف: ✅ **Capacitor Native متصل — طباعة Sunmi SDK مباشرة**
5. اضغط **Test Print** → فاتورة كاملة بالعربي بدون رموز

---

## 🔵 Bluetooth (طابعات خارجية)

التطبيق يستخدم `@capacitor-community/bluetooth-le`:
- **Native Permissions**: يطلب BLUETOOTH_SCAN/CONNECT تلقائياً
- **Real device search**: يجد كل الطابعات المقترنة + غير المقترنة
- **ESC/POS support**: عبر raster bitmap commands

---

## 📍 GPS

عبر `@capacitor/geolocation`:
- يطلب صلاحية ACCESS_FINE_LOCATION تلقائياً
- يعمل حتى لو الجهاز فيه Sunmi printer service
- دقة عالية (enableHighAccuracy: true)

---

## 🤖 البناء التلقائي عبر Codemagic

1. ارفع المشروع على GitHub
2. ربط GitHub بـ [Codemagic.io](https://codemagic.io)
3. ضع `codemagic.yaml` في root المشروع
4. كل push يبني APK تلقائياً ويبعتلك إيميل بالنتيجة

---

## 📞 الدعم

لو واجهت مشكلة في الـ build، شارك:
- Build log كامل من Android Studio / Codemagic
- إصدار Sunmi V2 (Settings → About)
- نسخة Android على الجهاز
