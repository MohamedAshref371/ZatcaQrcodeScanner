# صور Google Play

الملفات جاهزة للاستخدام في صفحة المتجر، ولا يتطلب استخدامها تغيير اسم التطبيق تحت الأيقونة.

| مكان الرفع في Play Console | الملف |
|---|---|
| App icon | `app-icon-512.png` — أيقونة PNG ‏512×512، ‏32 بت |
| Feature graphic — العربية | `feature-graphic-ar-1024x500.png` |
| Feature graphic — الإنجليزية | `feature-graphic-en-1024x500.png` |
| Phone screenshots — العربية | الصور الست في `screenshots/ar/` بالترتيب الرقمي |
| Phone screenshots — الإنجليزية | الصور الست في `screenshots/en/` بالترتيب الرقمي |

اللقطات PNG ‏1080×1920، ‏24 بت وبدون شفافية. جميع الفواتير ببيانات تجريبية، وأرقام ضريبية افتراضية. اللقطات من واجهة التطبيق الفعلية؛ لا تتضمن تعديلًا للمحتوى أو فتحًا وهميًا لباقة الداعم. الفحص المعروض محلي وبنيوي، ولا يعني اعتماد الفاتورة من الهيئة.

ترتيب اللقطات: نتيجة الفحص والمشاركة، تفاصيل الفاتورة ونسخ الرقم الضريبي، السجل والتصدير، النسخ الاحتياطي والاستعادة، المظهر الداكن، وإدخال Base64 يدويًا.

افتح `preview.html` لمعاينة المجموعة. يحتوي `google-play-images.zip` على صور الرفع وصفحة المعاينة وهذا الدليل؛ ملفات المصدر وأدوات التصوير خارج الأرشيف.

## إعادة إنشاء الرسومات

من جذر المشروع، باستخدام Chrome أو Edge المثبت محليًا:

```powershell
python tools/generate_store_graphics.py --browser 'C:/Program Files/Google/Chrome/Application/chrome.exe'
```

مصادر الرسومات القابلة للتعديل في `source/`. التصميم يستخدم أيقونة التطبيق الحالية، ويعمل دون خدمات توليد صور أو خطوط خارجية.

## إعادة تصوير الواجهة

استخدم محاكيًا مخصصًا للتصوير، وليس جهازك أو تثبيت التطبيق الذي يحتوي على فواتيرك. ابنِ `assembleDebug` و`assembleDebugAndroidTest` مع `-PisolatedValidation=true`، وثبّت الحزمتين على المحاكي. معرّف تطبيق التصوير ينتهي بـ`.validation`.

اضبط دقة المحاكي على 1080×1920 وكثافته على 360، وأوقف حركات النظام، واستخدم وضع العرض التجريبي لإخفاء الإشعارات وضبط الوقت والبطارية. ابدأ ببيانات فارغة لتطبيق التصوير لكل لغة. اضبط لغة التطبيق عبر `cmd locale set-app-locales` إلى `ar` أو `en`، ثم نفّذ:

```text
adb -s EMULATOR_SERIAL shell am instrument -w -e storeScreenshots true -e class com.poetscorner.invoiceqr.StoreScreenshotCapture com.poetscorner.invoiceqr.validation.test/androidx.test.runner.AndroidJUnitRunner
```

اسحب مجلد اللغة من `/sdcard/Android/data/com.poetscorner.invoiceqr.validation/files/store-captures/` إلى `screenshots/`. أداة التصوير تعمل فقط عند تفعيل الوسيط السابق وفي نسخة التصوير المنفصلة؛ تتخطاها الاختبارات العادية.

بعد التقاط اللغتين، حدّث المعاينة والأرشيف وتحقّق من مقاسات جميع الملفات:

```powershell
python tools/package_store_graphics.py
```

راجع الصور بصريًا قبل الرفع، خصوصًا بعد أي تغيير في الواجهة أو الترجمة. لم تُرفع هذه الملفات إلى Google Play تلقائيًا.
