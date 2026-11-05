# صور Google Play

الملفات جاهزة للاستخدام في صفحة المتجر، ولا يتطلب استخدامها تغيير اسم التطبيق تحت الأيقونة.

| مكان الرفع في Play Console | الملف |
|---|---|
| App icon | `app-icon-512.png` — أيقونة PNG ‏512×512، ‏32 بت |
| Feature graphic — العربية | `feature-graphic-ar-1024x500.png` |
| Feature graphic — الإنجليزية | `feature-graphic-en-1024x500.png` |
| Phone screenshots — العربية | الصور الثماني في `screenshots/ar/` بالترتيب الرقمي |
| Phone screenshots — الإنجليزية | الصور الثماني في `screenshots/en/` بالترتيب الرقمي |

اللقطات PNG ‏1080×1920، ‏24 بت وبدون شفافية. جميع الفواتير ببيانات تجريبية، وأرقام ضريبية افتراضية. اللقطات من واجهة التطبيق الفعلية؛ لا تتضمن تعديلًا للمحتوى. الفحص المعروض محلي وبنيوي، ولا يعني اعتماد الفاتورة من الهيئة.

ترتيب اللقطات: الكاميرا والفحص المتتابع، نتيجة الفحص والمشاركة، تفاصيل الفاتورة ونسخ الرقم الضريبي، السجل والتصدير، الإحصاءات، النسخ الاحتياطي والاستعادة، المظهر الداكن، وإدخال Base64 يدويًا.

لقطة الكاميرا تعرض معاينة حقيقية لمشهد المحاكي وفيه فاتورة تجريبية ذات QR؛ الصورة ليست مركّبة فوق واجهة التطبيق. وضع الفحص المتتابع مفعّل ليظل الماسح ظاهرًا عند قراءة الرمز. لقطة الإحصاءات تجمع ست فواتير تجريبية في ثلاث مجموعات بائعين، بإجمالي 805.00 وضريبة 105.00.

افتح `preview.html` لمعاينة المجموعة. يحتوي `google-play-images.zip` على صور الرفع وصفحة المعاينة وهذا الدليل؛ ملفات المصدر وأدوات التصوير خارج الأرشيف.

## إعادة إنشاء الرسومات

من جذر المشروع، باستخدام Chrome أو Edge المثبت محليًا:

```powershell
python tools/generate_store_icon.py --store-only
python tools/generate_store_graphics.py --browser 'C:/Program Files/Google/Chrome/Application/chrome.exe'
python tools/package_store_graphics.py
```

مصادر الرسومات الترويجية القابلة للتعديل في `source/`، ومصدر الأيقونة في `app-icon.svg`. رمز الفاتورة في أيقونة المتجر مكبّر 1.5 مرة ومتمركز؛ خيار `--store-only` يحدّث ملفات المتجر فقط. تحتفظ أصول مشغّل Android بهوامشها الخاصة بالأيقونة التكيفية.

الصورتان الترويجيتان تعرضان مقتطفًا من لقطة نتيجة الفحص الفعلية باللغة المطابقة، بجانب العنوان ومزايا المسح والسجل والإحصاءات والتصدير. التصميم يعكس توزيع العناصر بين العربية والإنجليزية، ويعمل دون خدمات توليد صور أو خطوط خارجية. أعد توليد الصور الترويجية بعد تحديث لقطة `02-result.png`، ثم أعد تجهيز الأرشيف.

## إعادة تصوير الواجهة

استخدم محاكيًا مخصصًا للتصوير، وليس جهازك أو تثبيت التطبيق الذي يحتوي على فواتيرك. ابنِ `assembleDebug` و`assembleDebugAndroidTest` مع `-PisolatedValidation=true`، وثبّت الحزمتين على المحاكي. معرّف تطبيق التصوير ينتهي بـ`.validation`.

اضبط دقة المحاكي على 1080×1920 وكثافته على 360، وأوقف حركات النظام، واستخدم وضع العرض التجريبي لإخفاء الإشعارات وضبط الوقت والبطارية. ابدأ ببيانات فارغة لتطبيق التصوير لكل لغة، وامنحه إذن الكاميرا. اضبط لغة التطبيق عبر `cmd locale set-app-locales com.poetscorner.invoiceqr.validation --user 0 --locales ar` أو `en`.

لتجهيز الفاتورة أمام الكاميرا، ثبّت Pillow وqrcode في بيئة أدوات منفصلة، ثم نفّذ لكل لغة:

```powershell
python tools/create_store_camera_scene.py --language ar --output build/store-camera-ar.png
```

استخدم كاميرا المحاكي الخلفية من نوع `virtualscene`، وحمّل الصورة بأمر `adb -s EMULATOR_SERIAL emu virtualscene-image wall ABSOLUTE_IMAGE_PATH`. انتقل إلى غرفة الصور باستخدام `emu automation play` مع ملف `emulator/resources/macros/Walk_to_image_room` داخل Android SDK. حافظ على هذه الوضعية عند تغيير اللغة، وحمّل الفاتورة الخاصة باللغة الجديدة. لا تعِد إنشاء Activity بين اللقطات حتى لا تُلتقط الواجهة أثناء إعادة حساب المسافات المحجوزة لأشرطة النظام.

ثم نفّذ:

```text
adb -s EMULATOR_SERIAL shell am instrument -w -e storeScreenshots true -e class com.poetscorner.invoiceqr.StoreScreenshotCapture com.poetscorner.invoiceqr.validation.test/androidx.test.runner.AndroidJUnitRunner
```

اسحب مجلد اللغة من `/sdcard/Android/data/com.poetscorner.invoiceqr.validation/files/store-captures/` إلى `screenshots/`. أداة التصوير تعمل فقط عند تفعيل الوسيط السابق وفي نسخة التصوير المنفصلة؛ تتخطاها الاختبارات العادية.

بعد التقاط اللغتين، حدّث المعاينة والأرشيف وتحقّق من مقاسات جميع الملفات:

```powershell
python tools/package_store_graphics.py
```

راجع الصور بصريًا قبل الرفع، خصوصًا بعد أي تغيير في الواجهة أو الترجمة. لم تُرفع هذه الملفات إلى Google Play تلقائيًا.
