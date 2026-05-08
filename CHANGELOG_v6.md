# GasTechPOS Android Wrapper — v6.0.0

## أهم الإصلاحات الجذرية

### 1. الطباعة العربية مثل Loyverse 100%
- **المشكلة السابقة**: الطابعة كانت تطبع رموز `<<<<<<` بدلاً من النص العربي لأن firmware Sunmi V2 لا يدعم رسم الحروف العربية كنص.
- **الحل**: الفاتورة الآن **تُرسم كصورة Bitmap كاملة** (نفس طريقة Loyverse) باستخدام Canvas + StaticLayout مع خط Cairo العربي المدمج، ثم تُرسل للطابعة عبر `IWoyouService.printBitmap()`.
- ملف جديد: `BitmapReceiptRenderer.java` — يحتوي كل منطق الرسم.
- خط Cairo Arabic مدمج في `app/src/main/assets/fonts/Cairo-Regular.ttf`.
- يدعم: شعار + هيدر + بيانات عميل + أصناف بأسماء عربية كاملة + مجاميع + QR code + فوتر + قص الورق.
- Word wrap تلقائي للنصوص الطويلة.
- يدعم نسختي `58mm` (384px) و `80mm` (576px).

### 2. دعم GPS كامل للعملاء
- إضافة صلاحيات `ACCESS_FINE_LOCATION` و `ACCESS_COARSE_LOCATION` في الـ Manifest.
- إضافة `WebChromeClient.onGeolocationPermissionsShowPrompt` يوافق تلقائياً على طلبات الموقع من الـ WebView.
- دالة جديدة `AndroidBridge.getCurrentLocation()` تعيد lat/lng مباشرة من `LocationManager` كـ fallback.
- زر "تثبيت الموقع" في صفحة الـ checkout يعمل الآن صح.

### 3. Bluetooth Printer (بحث + اتصال + طباعة)
- ملف جديد: `BluetoothPrintBridge.java` (مكشوف كـ `window.AndroidBT`):
  - `listPaired()` — قائمة الطابعات المقترنة
  - `connect(macAddress)` — يفتح socket على UUID SPP القياسي
  - `printJson(json)` — يرسم الفاتورة كـ Bitmap ويرسلها كـ ESC/POS raster (`GS v 0`)
  - `testPrint()` — اختبار سريع
  - `disconnect()`
- صلاحيات `BLUETOOTH_CONNECT` و `BLUETOOTH_SCAN` (Android 12+).

### 4. WhatsApp Handoff (محسّن من v5)
- نفس منطق v5 + إضافة `geo:` للـ intent filter (لـ Google Maps).

## الملفات المعدلة / المضافة
- ✅ `app/src/main/java/com/gastech/pos/PrinterHelper.java` — إضافة `printBitmap()`
- ✅ `app/src/main/java/com/gastech/pos/SunmiPrintBridge.java` — `printReceipt()` يستخدم Bitmap الآن
- ✅ `app/src/main/java/com/gastech/pos/MainActivity.java` — geolocation + bluetooth bridge + permissions
- ✅ `app/src/main/AndroidManifest.xml` — صلاحيات GPS + Bluetooth + queries
- ✅ `app/build.gradle` — versionCode 7, versionName 6.0.0, إضافة zxing
- 🆕 `app/src/main/java/com/gastech/pos/BitmapReceiptRenderer.java`
- 🆕 `app/src/main/java/com/gastech/pos/BluetoothPrintBridge.java`
- 🆕 `app/src/main/assets/fonts/Cairo-Regular.ttf`

## بناء النسخة
1. ارفع الـ ZIP على GitHub
2. شغّل بناء على Codemagic (نفس workflow القديم — لا تغيير في codemagic.yaml)
3. حمّل الـ APK وثبّته على Sunmi V2

## اختبار سريع بعد التثبيت
1. **طباعة عربية**: أعمل بيع منتج باسم عربي → اضغط طباعة → يفترض تطبع الفاتورة كاملة بالعربي بدون رموز.
2. **GPS**: في صفحة "إضافة عنوان" اضغط "تثبيت الموقع" → يطلب صلاحية الموقع → يحفظ lat/lng.
3. **Bluetooth**: في الإعدادات → نوع الطابعة Bluetooth → اضغط "بحث" → اختر طابعتك المقترنة → اختبار.
4. **WhatsApp**: من الفاتورة → إرسال واتساب → يفتح تطبيق واتساب مباشرة.
