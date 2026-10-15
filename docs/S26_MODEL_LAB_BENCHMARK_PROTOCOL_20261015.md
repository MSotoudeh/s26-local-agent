# S26 Model Lab — پروتکل بنچمارک استاندارد و بازتولیدپذیر

**نسخه:** 1.0.0 | **حالت فعلی:** specification؛ اجرای مدل‌های جدید هنوز شروع نشده است.
**ورودی ماشینی:** `android/model-lab/catalog.json` و `android/model-lab/scenarios.json`.

## A. اصل اندازه‌گیری

بدون تعریف یک `ExperimentReceipt` یکسان، مقایسهٔ دو مدل معتبر نیست. هر نتیجه باید شامل شناسهٔ مدل و Artifact/Quant، SHA-256، commit Runtime، نوع backend (CPU/GPU/NPU با مدرک)، device SoC/ABI/API، دمای شروع و پایان، درصد باتری، `MemAvailable`، seed، top_p/top_k/temp، context و طول خروجی، hash دیتاست و prompt، timestamp و هر نوع warm/cold بودن باشد. ادعای NPU صرفاً بر اساس ویژگی گوشی ممنوع؛ باید delegate و actual device logs تأیید کنند.

در اجرای مقایسهٔ A/B روی گوشی، گزینه‌ها ترتیبی اجرا می‌شوند. برای جلوگیری از اختلاف کیفیت به دلیل Auto-VAD، حالت‌های `fixed-audio` و `end-to-end-microphone` جداگانه گزارش شوند. خروجی صوت/اسکرین واقعی بدون رضایت مالک ذخیره/آپلود نشود.

## B. آزمایش‌های ASR

- **مجموعهٔ فارسی:** حداقل ۱۰۰ فایل waveform با مرجع اصلاح‌شده، از جمله ۲۰ فرمان تنظیمات، ۲۰ جفت تضاد `روشن / خاموش`، ۱۵ عبارت نام‌دار، ۱۰ عبارت عدد، ۱۵ پرسش مکالمه‌ای و ۲۰ نمونهٔ نویز/مخلوط انگلیسی. سهم‌های نمونه قابل تغییر است؛ تعداد کل حداقل ۱۰۰.
- هر مدل: یک فایل صوتی 16k/mono PCM canonical و مرجع Unicode normalized. Normalize فقط ی/ک عربی، ZWNJ، علائم و فاصله‌ها؛ **هیچ اشتباه معنایی در روشن/خاموش normalize نمی‌شود**.
- شاخص‌ها: `WER`، `CER`، `command_exact_match`، `intent_accuracy`، `false_positive_execution_rate` (هدف **صفر در تست**)، `abstention_rate`، `RTF`، `p50/p95` زمان decoding و peak RSS/PSS.
- برای فرمان حساس، مدل حق اجرای خودکار ندارد؛ صرفاً در Sandbox و dry-run router مقایسه شود. در آزمایش real voice، فقط R0های whitelist اجرا شوند.

## C. آزمایش‌های مکالمه/LLM

- ۶۰ prompt فارسی ثابت، با ۱۰ سؤال DevOps/Kubernetes، ۱۰ رفع اشکال، ۱۰ پرسش روزمره، ۱۰ چندنوبتی/حافظه، ۱۰ پرسش با شواهد ناکافی/نیازمند امتناع، و ۱۰ دستور با ادعای نیاز به دسترسی حساس.
- ۳ تنظیم benchmark: `short(512 ctx)`، `medium(2048)`، `long(4096+ only if device safe)`. مقایسهٔ یک context برابر جدا از آزمون `max_supported_context`.
- `TTFT`، `prefill tok/s`، `decode tok/s`، `output tokens`، `tokens/sec/watt` (فقط اگر ابزار سنجش توان معتبر)، peak RAM، quality rubric (0–5) و hallucination counter.
- نتیجهٔ مدل‌هایی که طول پاسخ متفاوت داده‌اند با tokens/sec در کنار latency مجموع گزارش شود، نه رتبه‌بندی صرفاً بر سرعت خام.

## D. آزمایش TTS

- ۴۰ متن فارسی برای تلفظ اعداد، تاریخ، واحدها، علائم، انگلیسی-فارسی، نام، واژهٔ فنی و جملات کوتاه/بلند.
- صوت از هر voice با sample rate واقعی ذخیرهٔ موقت می‌شود؛ پیش از داوری از نام مدل anonymize شود.
- `time_to_first_audio`، `synthesis_RTF`، طول خروجی، نرخ dropout، MOS انسانی (۱–۵)، intelligibility، pronunciation accuracy، loudness و تأخیر barge-in.
- مقایسهٔ A/B بین صداهای Piper و صوت Android System فقط وقتی Android صوت را واقعاً *آفلاین* تولید کند.

## E. VLM/Screen

- حداقل ۳۰ fixture مصنوعی (صفحهٔ تنظیمات، دیالوگ، متن فارسی، نمودار و فرم) با ground truth؛ درج PIN/OTP واقعی ممنوع.
- تست tree-only، OCR-only، image+tree و vision-only مستقل؛ accuracy و groundedness و امتناع از حدس اندازه‌گیری شوند.
- محتوا فقط پس از دستور صریح «به صفحه نگاه کن» وارد RAM شود؛ privacy check پیش از اسکرین‌شات؛ در foreground رمز/بانک تلاش به screenshot نکنیم. تصویر واقعی به API بیرونی نفرستیم مگر opt-in جداگانهٔ کاربر.

## F. ابزارهای آنلاین

- Benchmark offline vs online جدا؛ هر search receipt شامل provider، query، count، URLهای واقعی، `cost=0` یا هزینهٔ واقعی تأییدشده، duration، failure و تعداد توکن زمینه.
- HTTPS provider adapter؛ ذخیرهٔ token در Android Keystore، نه Source/Log؛ ابتدا نمونهٔ بدون هزینه مثل جست‌وجوی مجاز یا self-hosted backend بررسی شود؛ **بدون مجوز، سرویس پولی فعال نشود**.

## G. اندازه‌گیری مصرف منابع

- Sampling اولیهٔ ۱ ثانیه برای حافظه و وضعیت حرارت/باتری با `ActivityManager.MemoryInfo`, `Debug.MemoryInfo`, `PowerManager.getCurrentThermalStatus` و `BatteryManager`. هرکدام در صورت نبود API باید `unavailable` ثبت شود، نه صفر ساختگی.
- power(mW)، NPU utilization و GPU utilization را فقط با ابزار تأییدشده اندازه بگیریم؛ صرف CPU/GPU flag بدون telemetry اثبات NPU نیست.
- Load/Unload: `selection_to_ready_ms`, `cold_load_ms`, `warm_load_ms`, `unload_ms`, resident after unload, error class and rollback.
- تست سنگین ۳ اجرا cold و ۳ warm، در ابتدا و انتها وضعیت حرارت؛ ترتیب مدل‌ها randomized balanced با زمان cool-down. برای ۹B/۱۴B اگر budget gate برقرار نبود، نتیجه `SKIPPED_MEMORY_GATE` است نه failure سیستم.
- برای حافظهٔ آزاد نمونه‌ای دستگاه: MemAvailable حدود ۴٫۷ GiB بود؛ برای quant 9B و 14B باید قبل از اجرا budget واقعی + fragmentation محاسبه شود.

## H. سیاست ایمنی و توقف

| شرط | سیاست |
|---|---|
| Battery آغاز Heavy کمتر از ۵۰٪ | اجرای Heavy آغاز نشود |
| Android ThermalStatus >= SEVERE | فوراً لغو inference، ثبت و unload |
| MemAvailable پایدار کمتر از ۱ GiB | متوقف، unload، فاصلهٔ استراحت |
| زمان هیچ خروجی بیش از ۹۰ ثانیه | watchdog و نتیجه TIMEOUT |
| Activity ANR یا process death | timeout receipt + rollback فعال |
| فضای آزاد کمتر از ۳۰ GB | دانلود جدید متوقف، حذف خودکار انجام نشود |
| مدل دارای مجوز NC/gated | جداشده، صرفاً با اجازه و پذیرش شرایط |
| هم‌زمان اجرای R0 واقعی و Benchmark | ممنوع؛ رزرو ModelLab run lock |

حدود فوق آستانه‌های اولیه‌اند و روی گوشی با آزمایش غیرمخرب بازبینی می‌شوند.

## I. ساختار نتیجه و قرارداد گزارش

```json
{
  "schema_version": "1.0.0",
  "scenario_id": "asr.fa.toggle-negation",
  "input_sha256": "<sha of one consented or public fixture>",
  "model_id": "asr.qwen3.0_6b.int8",
  "artifact_sha256": "<real hash after install>",
  "runtime": {"name": "sherpa-onnx", "version": "<pinned>", "backend": "cpu"},
  "device": {"model": "SM-S948B", "soc": "SM8850", "android_api": 37},
  "config": {"context": null, "quant": "int8", "seed": null},
  "run": {"mode": "cold", "iteration": 1, "status": "not-executed"},
  "metrics": {"latency_ms": null, "wer": null, "cer": null,
    "peak_pss_mib": null, "thermal_status": null, "battery_delta_pct": null},
  "provenance": {"app_commit": "<git-sha>", "dataset_version": "fa-benchmark-v1"}
}
```

`null` یعنی **اندازه‌گیری‌نشده**، نه صفر. نتایج خام هر run immutable باشند. Export CSV/JSON باید model/source/revision را همیشه حفظ کند و معادل واقعاً قابل تکرار باشد.

## J. امتیازدهی سناریومحور و گزارش

هیچ «بهترین مدل مطلق» نداریم. جدول رتبه برای سه هدف جدا باشد:
- **Fast:** ۴۰٪ latency، ۲۵٪ RAM، ۲۰٪ accuracy و ۱۵٪ مصرف انرژی.
- **Balanced Persian:** ۴۰٪ دقت/فهم فارسی، ۲۵٪ latency، ۲۰٪ stability، ۱۵٪ RAM.
- **Quality/Heavy:** ۵۰٪ دقت و grounding، ۲۰٪ stability، ۱۵٪ latency، ۱۵٪ RAM؛ *hard eligibility gate* قبل از امتیاز.
- **Screen:** grounding و عدم افشای اطلاعات حساس شرط ورود است، سپس quality و latency.

این وزن‌ها policyهای اولیه‌اند؛ UI اجازهٔ تغییر وزن و بازحساب رتبه‌ها بر روی نتایج خام ذخیره‌شده را می‌دهد. جمع وزن هر preset باید ۱۰۰٪ باشد. گزارش دارای CI، تعداد نمونه، P50/P95 و خطاهای جداگانه باشد. میانگین نتایج یک اجرا را «ثابت‌شده» ننامیم.

## K. تحویل و تکرارپذیری

- `scenarios.json` فقط **تعریف public/synthetic fixture** است، نه ضبط‌های شخصی. هنگام ساخت دیتاست، hash و consent flag به `dataset-manifest.json` اضافه شود.
- اجرای A/B: هر Input یک‌بار ضبط یا از فایل خوانده شود، همان byte stream به همهٔ Adapterها داده شود؛ خروجی‌های خام و نمره جدا ذخیره شود.
- شیوهٔ اجرا: `prepare → model X cold/warm → unload → cool-down → model Y cold/warm → compare → rollback-to-current-default`.
- پایان هر Milestone: regression Python/Java/Android، سلامت Accessibility، نقش Assistant، پایداری Agent و Shizuku اختیاری، گزارش منابع، hash artifact و Git checkpoint.
