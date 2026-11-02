# قياس أداء مسح الكاميرا على Samsung SM-A205F

تاريخ القياس: 2026-11-02. الجهاز: Samsung SM-A205F، Android 11. استُخدمت نسخة
التحقق المنفصلة `com.poetscorner.invoiceqr.validation`، ولم يُستبدل التطبيق المعتاد.

## المنهج

- المحرك: ZXing؛ الإعداد المطلوب: HD و`FASTEST` (فاصل 50 مللي ثانية بين محاولات التحليل).
- دقة إطار التحليل الفعلية التي اختارتها CameraX: `960×720`، وليست دقة HD المطلوبة حرفيًا.
- بعد وصول 3 إطارات واستبعاد فترة بدء الكاميرا، جُمعت عينة مدتها 10 ثوانٍ مع قص منطقة المسح، ثم عينة 10 ثوانٍ بدونه. تكرر الاختبار مرتين.
- `main+crop` يشمل انتظار تنفيذ حساب إحداثيات القص على الخيط الرئيسي وحسابها. `decode` يقيس قراءة ZXing. `total` مجموعهما، ولا يشمل عرض النتيجة أو حفظها.
- لم يدخل QR مجال الكاميرا أثناء الاختبار (`qrFound=0` في العينات الأربع). الأرقام تقيس تكلفة البحث في إطار بلا QR، وليست زمن العثور على فاتورة فعلية.

| الجولة | قص المنطقة | الإطارات/10 ثوانٍ | `main+crop` p50 / p95 | `decode` p50 / p95 | `total` p50 / p95 |
|---|---|---:|---:|---:|---:|
| 1 | نعم | 166 | 2.2 / 4.6 ms | 5.7 / 9.5 ms | 8.3 / 13.1 ms |
| 1 | لا | 165 | 1.4 / 2.9 ms | 13.9 / 19.7 ms | 15.8 / 21.9 ms |
| 2 | نعم | 163 | 2.1 / 3.3 ms | 6.0 / 7.3 ms | 8.4 / 10.3 ms |
| 2 | لا | 165 | 1.6 / 2.4 ms | 15.2 / 21.5 ms | 16.8 / 23.3 ms |

مع القص، كان زمن `total` الوسيط 8.3–8.4 ms مقابل 15.8–16.8 ms بدون قص.
الانتظار والحساب على الخيط الرئيسي كان p95 عند 3.3–4.6 ms مع القص.
هذه النتائج لا تبرر تغيير آلية القص الآن؛ الإبقاء على القص مفعّلًا افتراضيًا قرار مناسب لهذا الجهاز.

## إعادة القياس

ابنِ نسخة التحقق مع JBR التي يستخدمها المشروع، وثبّتها على الجهاز المستهدف، وامنحها
صلاحية الكاميرا. شغّل `CameraScanPerformanceTest` فقط. يطبع الاختبار الأرقام في
`logcat` تحت الوسم `CameraScanPerformance`. يعيد إعدادات نسخة التحقق الأصلية بعده.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio1\jbr'
.\gradlew.bat -PisolatedValidation=true :app:assembleDebug :app:assembleDebugAndroidTest
adb -s DEVICE_ID install -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE_ID shell pm grant com.poetscorner.invoiceqr.validation android.permission.CAMERA
adb -s DEVICE_ID install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s DEVICE_ID shell am instrument -w -e class com.poetscorner.invoiceqr.CameraScanPerformanceTest com.poetscorner.invoiceqr.validation.test/androidx.test.runner.AndroidJUnitRunner
adb -s DEVICE_ID logcat -d -s CameraScanPerformance:I '*:S'
```

للحكم على سرعة ظهور النتيجة للمستخدم، يلزم قياس إضافي بفاتورة QR معروفة مطبوعة أو
معروضة أمام الكاميرا، في إضاءات ومسافات مختلفة، مع تشغيل المحركين إن كان ML Kit متاحًا.
