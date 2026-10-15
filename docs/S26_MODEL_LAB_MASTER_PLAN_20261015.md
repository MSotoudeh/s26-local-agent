# طرح اجرایی S26 Model Lab — آزمایشگاه چندمدلی دستیار فارسی

**نسخهٔ طرح:** 2026-10-15 | **وضعیت:** PLAN ONLY (مدل‌های جدید این طرح هنوز نصب نشده‌اند)
**پایهٔ کد:** `project-ir`، کامیت `205e08c`، مسیر Voice Assistant آفلاینِ نصب‌شده
**خروجی مورد انتظار:** یک آزمایشگاه واقعی روی **خود گوشی** با نصب چند مدل، سوییچ ساده، A/B منصفانه، تست مدل‌های سنگین و سپس انتخاب بهترین ترکیب برای دستیار دائمی.

## 0. خواسته و تعریف موفقیت

خواسته فقط تعویض Qwen نیست. باید بتوانیم برای **ASR، VAD، TTS، LLM، VLM، OCR، Runtime و Search** چند آلترناتیو هم‌زمان روی *storage* نگه داریم؛ با UI واحد مدل فعال هر مرحله را انتخاب کنیم، یک صوت/پرامپت/تصویر ثابت را به چند مدل بدهیم، نتیجه و مصرف منابع را در یک گزارش تکرارپذیر مقایسه کنیم. نصب به معنی load شدن هم‌زمان نیست.

**اصل مهم:** مدل‌های سازگار و مجاز را روی گوشی نصب و نگه می‌داریم؛ فقط مدل فعال یا یک زوج سبک ضروری در RAM می‌مانند. مدل‌های بسیار بزرگ و مدل‌های دارای محدودیت مجوز با وضعیت `not_auto_install` یا `gated_approval` ثبت می‌شوند و برای پرکردن لیست، گوشی را به OOM یا نقض مجوز نمی‌بریم.

### داده‌های مستقیماً مشاهده‌شده، نه تخمینی

| داده | اندازهٔ مشاهده‌شده | تبصره |
|---|---:|---|
| دستگاه | SM-S948B، SoC=SM8850، Android API=37 | از ADB |
| RAM کل Kernel (`MemTotal`) | 11,350,972 KiB ≈ 10.83 GiB | «۱۲ GB RAM» بازاری با مقدار سیستم متفاوت است |
| `MemAvailable` در یک Snapshot | 4,957,864 KiB ≈ 4.73 GiB | دائماً تغییر می‌کند؛ ظرفیت مدل از روی این عدد تضمین نمی‌شود |
| فضای آزاد `/data` | حدود 161 GB | قابل‌تغییر؛ قبل از هر نصب دوباره چک شود |
| دستیار پیش‌فرض | `io.projectir.mobile` | حفظ شود |
| خط مبنا | Vosk fa-small + Qwen2.5-0.5B، Accessibility، LocalPlanJournal | در گوشی نصب و آزمایش شده |
| وضعیت Shizuku | Ready در Hub | در حلقهٔ فعلی صوت/ASR/LLM لازم نیست |

## 1. ساختار نهایی درون Android

```text
Side key / Assistant / chat / test runner
             |
         SessionManager -------------------- Consent + PrivacyGate
             |
     AudioCapture (foreground only) --> VAD Adapter
             |                           (WebRTC / Silero)
         ASR Adapter <----- installed catalog/model store
     (Vosk / Qwen3-ASR / Omni / Whisper...)
             |
  Transcript and Confidence + Correction UI
             |
    Intent Router + Policy / ReadOnly Qwen
          |               |               |
 Canonical R0 Skills   Conversation     Research / Search Adapter
 (Accessibility)       memory                 (online opt-in)
          |               |               |
          +----------- Response Composer -----------+
                         |
       TTS Adapter (Piper voices / Android OS / research)
                         |
       Speaker playback + barge-in / turn endpoint
```

در مسیر «به صفحه نگاه کن» دو منبع مجزا داریم: **Accessibility UI tree** برای متن و ساختار عناصر؛ **گرفتن یک Screenshot با درخواست صریح** برای VLM و OCR. محتوای پنجره‌های امن، رمزها، OTP و فیلدهای حساس نباید به مدل/اینترنت فرستاده شود. دریافت Image همیشه پس از اقدام کاربر؛ ذخیرهٔ پیش‌فرض ممنوع.

### قرارداد مشترک Runtime

```kotlin
interface LocalModelAdapter {
  suspend fun prepare(modelId: String, options: LoadOptions): LoadReceipt
  suspend fun warmup(): WarmupReceipt
  suspend fun infer(input: TaskInput, options: InferenceOptions): TaskResult
  suspend fun cancel(requestId: String)
  suspend fun unload(): UnloadReceipt
  fun capabilities(): Set<Capability>
  fun metrics(): RuntimeMetrics
}
interface ModelStore {
  suspend fun install(request: InstallRequest): InstallReceipt
  suspend fun verify(modelId: String): VerificationReceipt
  suspend fun remove(modelId: String): RemovalReceipt
}
interface ModelSwitcher {
  suspend fun activate(task: Capability, modelId: String): SwitchReceipt
  suspend fun revertToLastKnownGood(task: Capability): SwitchReceipt
}
```

**Adapterها:** `vosk-android`، `sherpa-onnx`، `whisper.cpp`، `piper-onnx`، `Android TextToSpeech`، `mediapipe/LiteRT`، `llama.cpp GGUF`، `llama.cpp multimodal` و در صورت سازگاری `Qualcomm AI Hub / NPU`. هیچ مدل را به Runtime نامتناسب تحمیل نکنیم. `modelId` شناسهٔ *نسخه + فرمت + quantization* است، نه صرفاً اسم برند.

**تغییر مدل:** از UI یک انتخاب انجام می‌شود؛ عملیات `cancel-inflight → unload old → reclaim memory → verify hash/ABI → load new → warmup → health-check → atomic active-pointer update` باید Transactional باشد. در خطا، *پیکربندی قبلی* فعال شود؛ rollback نباید مدل نیمه‌بارگذاری‌شده را Active معرفی کند. فایل‌های مدل برای تعویض باقی می‌مانند. سرعت تغییر صفحهٔ انتخاب فوری است؛ **زمان load مدل سنگین اندازه‌گیری می‌شود و نباید زیر یک ثانیه وعده داده شود**.

## 2. Catalog نصب چندمدلی

**منابع machine-readable:** `android/model-lab/catalog.json` دارای ۳۱ ورودی مدل با source، licence، runtime، task، tier و وضعیت واقعی است. `android/model-lab/providers.json` چهار انتخاب Search، چهار مسیر OCR و چهار backend محاسباتی را برای مقایسهٔ مستقل ثبت می‌کند. فقط `installed_verified` نصب فعلی است؛ `planned` به معنای تصمیم و برنامه است نه نصب موفق.

| حوزه | کاندیدهای قابل ارزیابی و نصب تدریجی | Gate |
|---|---|---|
| ASR سریع/متوسط | Vosk small-fa (Baseline)، Qwen3-ASR 0.6B Int8، Meta Omnilingual CTC 300M Int8 | دقت فارسی + Android runtime |
| ASR سنگین | OmniASR CTC 1B Int8؛ Whisper small/medium/large-v3-turbo/large-v3 quant و FP16 | محدودیت RAM، تأخیر و باتری |
| VAD | WebRTC VAD، Silero VAD ONNX | خطای قطع زودهنگام، false barge-in |
| TTS | Android System voice آفلاین، Piper: amir / ganji / ganji_adabi / gyro / reza_ibrahim | مجوز هر صدا + کیفیت تلفظ |
| TTS تحقیقاتی | Meta MMS-TTS-fas | **CC-BY-NC**؛ فقط جداسازی تحقیق، نه محصول تجاری |
| LLM سبک تا قوی | Qwen2.5-0.5B فعلی؛ Qwen3.5-0.8B، 2B، 4B، 9B | quantization و حافظه |
| LLM فشار | Qwen3-14B Q2/Q3؛ Qwen3-32B فقط به‌عنوان حد فراتر از بودجهٔ عملی | امتناع از OOM/ANR عمدی |
| VLM | Qwen3.5-2B/4B، Qwen3-VL-2B، Gemma 3n E2B/E4B | projector، screenshot، مجوز |
| متن صفحه | Accessibility tree، تصویر screen، ML Kit OCR | Permissions و Privacy |
| Web search | Adapter برای HTTPS search با تأیید آنلاین؛ Local-only fallback | هزینه، کوتا، حریم خصوصی |

**تفاوت Quantization:** نسخه‌های Q2/Q3/Q4/Q5/Q8 هرکدام یک artifact و یک مدخل آزمایشی مستقل دارند؛ نتایجشان قابل جایگزینی کورکورانه نیست. برای مقایسهٔ کیفیت، مدل و context و prompt را ثابت نگه می‌داریم؛ برای سنجش قدرت سخت‌افزار، precision/backend را نیز در ماتریس اجرا تغییر می‌دهیم.

**اخطار:** نام مدل در Hugging Face به معنی آماده بودن نسخهٔ Android/NNAPI/NPU/GGUF نیست. فقط URL/Artifact واقعاً موجود با checksum و manifest منطبق به `installable` تبدیل می‌شود. برای **Gemma 3n** باید مالک دستگاه شرایط مجوز را خودش بپذیرد؛ دانلود خودکارِ محدودیت‌دار ممنوع است. `MMS-TTS-fas` غیرتجاری است؛ وارد بستهٔ فروش نشود. برای Piper مجوز هر صدای مشخص جداگانه بررسی شود.

## 3. فضای نصب و مدیریت مدل‌ها

- `/sdcard/Android/data/io.projectir.mobile/files/models-lab/<task>/<model-id>/<revision>/` فقط برای artifactهای عمومی؛ فهرست و نتایج آزمایش در storage خصوصی برنامه. فایل‌های خصوصی کاربر هرگز در مدل‌فولدر عمومی ذخیره نشوند.
- Download Manager: resumable، `.partial`، checksum SHA-256 و revision pin، verification پس از copy به گوشی و atomic rename؛ شکست دانلود باعث حذف مدل سالم قبلی نمی‌شود.
- Model Index: `available → downloading → downloaded → verified → runtime_compatible → installed → active → failed/quarantined`. `installed` فقط بعد از smoke load موفق و `active` فقط بعد از health-check.
- حجم و سهمیه: **حداقل ۳۰ GB آزاد** همیشه باقی بماند. سقف آزمایشگاهی اولیهٔ کل وزن‌ها و cache **۱۱۰ GB**، سقف موقت temp files جدا و یک download بزرگ هم‌زمان.
- قبل از دانلود به ADB/USB تکیه می‌کنیم؛ مدل روی `ops` دریافت و Hash می‌شود، سپس به گوشی منتقل می‌شود. بعد از نصب، inference باید مستقل از PC/Hub باشد. دانلود مستقیم روی گوشی فقط در شبکهٔ Wi-Fi غیر‌محدود و با رضایت مالک.
- حذف: فقط از Catalog مدل اضافه‌شده و با تأیید مالک؛ Vosk و Qwen فعلی، APK rollback، Telegram و داده‌های برنامه حذف نشوند. حذف هیچ مدل صرفاً به دلیل بدبودن بنچمارک خودکار نیست.
- مدیریت RAM: حداکثر **یک LLM/VLM سنگین** در حافظه؛ ASR یا TTS سبک به اندازهٔ حد RAM آزاد. Adapterها `unload` و فشار حافظه/thermal callback داشته باشند.

## 4. UI کاربر: Model Lab در خود گوشی

چهار Tab: **Assistant** (استفادهٔ معمول)، **Models** (Installed/Downloadable/License/Hash/Activate)، **Benchmarks** (سناریوهای قابل اجرای تکی و batch)، **Compare** (نتایج). در Assistant چهار Dropdown مجزا `ASR / LLM-VLM / TTS / VAD` و یک `Profile` می‌آید.

Presetها:
- **Fast Offline**: ASR کوچک + LLM سبک + TTS سریع؛ پاسخ کوتاه، حداقل انتظار.
- **Balanced Persian**: بهترین نتیجهٔ آزمایش فارسی با تأخیر قابل قبول.
- **Quality**: ASR/LLM قوی‌تر؛ بعد از کنترل حرارت و رم.
- **Screen Explain**: VLM مناسب + Accessibility context؛ داده فقط در حافظه.
- **Heavy Lab**: مدل ۹B/۱۴B با context محدود و جداسازی session.
- **Online Research**: انتخاب صریح Web Search، همراه با منابع و مصرف شبکه.
- **Custom**: تغییر دستی همهٔ Adapterها و پارامترها (threads، n_ctx، quant، temperature، seed، output tokens، image size).

برای مقایسهٔ سریع دو مدل روی **یک ورودی ضبط‌شده/ثابت**: `A/B` ترتیبی اجرا می‌شود؛ نه موازیِ سنگین. در پایان دو خروجی، latency، مصرف RAM/باتری/حرارت و امتیاز سناریو کنار هم هستند. دکمه‌های `Replay`، `Export JSON/CSV`، `Copy reproducibility receipt` و `Revert` الزامی‌اند.

## 5. بنچمارک منصفانه (نه فقط یک عدد)

**فایل مستقل:** `docs/S26_MODEL_LAB_BENCHMARK_PROTOCOL_20261015.md`، داده‌های قابل تکرار `android/model-lab/scenarios.json`.

مجموعه آزمون:
1. ASR: حداقل ۱۰۰ utterance واقعی فارسی (تلفظ مختلف، بدون نویز/با نویز، عدد، انگلیسی در فارسی، فرمان‌های `روشن/خاموش`)؛ WER/CER، *intent accuracy*، false execute، real-time factor و p50/p95. یک فایل صوتی ضبط و به همهٔ مدل‌ها *همان فایل* داده شود، نه ضبط‌های متفاوت؛ اصلاح متن مرجع با تأیید کاربر.
2. TTS: ۴۰ متن فارسی همسان شامل نام‌های خاص، اعداد، انگلیسی در فارسی؛ MOS انسانی blind، naturalness، pronunciation، TTFB، latency تا پایان، RTF.
3. LLM: ۶۰ سؤال فارسی چندنوبتی، reasoning، DevOps، context طولانی و کنترل hallucination؛ TTFT، decode tokens/s، peak PSS/RSS، accuracy/grounding، موفقیت مکالمه و prompt version.
4. VLM/Screen: ۳۰ Screenshot ساختگی/غیرحساس با ground truth فارسی؛ OCR exactness، شناخت icon/button و abstention روی تصویر مبهم. **Screenshot واقعی خصوصی فقط با اقدام صریح، بدون انتقال/ذخیرهٔ پیش‌فرض**.
5. ابزار/Web: جست‌وجوی ۲۰ پرسش با پاسخ مستند و لینک؛ ابزارهای حساس و اجرا محدود؛ تعداد درخواست، هزینهٔ واقعی (در صورت وجود)، زمان، منبع و خطا.
6. Integrated end-to-end: حداقل ۳۰ turn چندنوبتی شامل توقف TTS، تغییر ASR، cancel، افت اتصال، بی‌پاسخی، background و UI transition؛ task success و no-duplicate-effect.
7. Heavy/thermal: اجرای کوتاه و طولانی model 2B→4B→9B→14B؛ `load time`, `TTFT`, `tok/s`, peak memory, battery drain, temperature/status, throttling ratio, crash/ANR, swap/recovery؛ ۳ تکرار cold و ۳ warm.

**شروط یکسان بودن:** دستگاه/OS، فایل ورودی، sampling، prompt/version، max output، context، quant backend، شبکه، battery bucket، thermal bucket، ۵ دقیقه idle baseline و عدم اجرای Task دیگر. مدل‌ها در ترتیب تصادفی متعادل اجرا شوند تا اثر گرم‌شدن اول/آخر bias نشود.

### سیاست توقف خودکار

- پیش از اجرای Heavy: حداقل ۵۰٪ شارژ، `MemAvailable` حداقل ۲ GiB (به‌علاوهٔ بودجهٔ موردنیاز تخمینی مدل)، فضای آزاد حداقل ۳۰ GB، thermal state پایین‌تر از Severe. این‌ها **مقادیر اولیهٔ محافظه‌کارانه** هستند و پس از تست اصلاح می‌شوند.
- در حین اجرا: Thermal status `SEVERE` یا بالاتر، `MemAvailable` کمتر از ۱ GiB برای بیش از پنجرهٔ نمونه‌برداری، خطای allocation، ANR/kill، افت شدید responsiveness، یا افت سریع شارژ → توقف آزمایش و unload، نتیجه `RESOURCE_LIMIT` به جای تلاش مجدد نامحدود.
- بدون مجوز روت، stress-test از روش application-level انجام می‌شود. **عمداً OOM یا crash دستگاه ایجاد نکنیم.** اگر ۱۴B Q2 هم در gate جا نشد، `NOT_FEASIBLE_ON_THIS_DEVICE` نتیجهٔ معنادار است؛ ۳۲B روی PC مرجع بررسی می‌شود.
- هر تست حداکثر زمان مشخص، watchdog و دکمهٔ `Stop All Benchmarks` دارد. Benchmarks باعث خاموش/روشن‌شدن خودکار Wi-Fi، reboots یا متوقف‌کردن دیگر Agentها نمی‌شوند.

## 6. فازهای تحویل و گیت پذیرش

| فاز | کار اجرایی | خروجی قابل آزمون |
|---|---|---|
| M0 — Catalog/Plan | Manifest، لیست منابع/مجوزها، مشخصات گوشی، طرح و پروتکل | **همین کامیت**؛ صفر تغییر روی برنامهٔ زنده |
| M1 — Runtime Abstraction | Adapter API، ModelStore، SHA/cancel/rollback، metadata و مسیر نصب | Unit/Instrumentation، نصب/سوییچ ۲ مدل کوچک |
| M2 — Multi-ASR + VAD | Vosk، Whisper small/turbo، Qwen3-ASR، Omni 300M، Silero/WebRTC | ۱۰۰ نمونهٔ فارسی، WER/intent + Live smoke |
| M3 — Multi-TTS | Piper ۵ صدا + Android System offline، MMS صرفاً تحقیقاتی | پخش صوت و ارزیابی blind انسانی |
| M4 — Multi-LLM | Qwen2.5 baseline، Qwen3.5 0.8/2/4/9، backend GGUF/LiteRT | مدل‌ها نصب، انتخاب در UI، A/B JSON/CSV |
| M5 — VLM/Screen | Qwen3.5 multimodal، Qwen3-VL 2B، Gemma 3n با مجوز، OCR | توضیح امن screenshot و ۳۰ fixture |
| M6 — Conversation/Search | نوبت‌های گفتار دوطرفه، TTS، barge-in، memory خصوصی، Web opt-in | E2E ۳۰ turn و امتیاز حریم خصوصی |
| M7 — Heavy Lab | 9B و 14B، stress-ramp و در صورت امکان NPU/GPU | گزارش دما/رم/پرفورمنس، درجهٔ عملی‌بودن |
| M8 — Selection/Rollout | امتیازدهی سناریومحور، default presets، recovery تست USB-off/Wi-Fi-off | بهترین stack برای Fast/Balanced/Quality، rollback verified |

**معیار GO هر فاز:** (الف) Build/Lint و ابزارهای امنیتی Native ELF 16 KB/ZIP، (ب) آزمون Unit/Device، (ج) مجوز و Hash، (د) مقایسهٔ reproducible، (هـ) اثر نکردن روی Agent فعلی و چت‌های هم‌زمان، (و) checkpoint در Git. هیچ مرحله‌ای صرفاً با «فایل دانلود شد» PASS نیست.

## 7. ترتیب اجرای پیشنهادشده و ریسک‌ها

ابتدا Manager/Comparer را می‌سازیم؛ بعد مدل‌ها را **دسته‌به‌دسته** روی storage گوشی نصب می‌کنیم. نصب همهٔ مدل‌های بزرگ قبل از اینکه بتوانیم صحت، RAM و کیفیت آنها را بسنجیم اتلاف پهنای باند و منابع است. اول Piper + Whisper + Qwen3.5-2B را با UI سوییچ؛ سپس Qwen3-ASR/Omni + سایر صداها؛ بعد 4B/9B/14B و VLM.

خطرهای اصلی: محدودیت Android runtime برای فرمت مدل، نداشتن pipeline vision در GGUF، 16 KB native ELF، مجوزهای غیرتجاری، دمای SoC، کرش Application هنگام سوییچ، تفاوت کیفیت فارسی، باگ تکرار فرمان، و لو رفتن صفحه/صدای خصوصی. هر خطر owner/mitigation و آزمون مستقل دارد و قبل از انتشار حل یا `HOLD` می‌شود.

## 8. تعریف دقیق «اتمام پروژه»

- برای **هر دسته** حداقل ۲ alternative *واقعاً نصب‌شده و قابل‌سوییچ*؛ همهٔ گزینه‌های **سازگار و مجاز** Catalog بعد از Gate روی storage نگهداری شده یا با دلیل مستند `UNSUPPORTED / GATED / NONCOMMERCIAL / MEMORY_LIMIT` باقی مانده‌اند.
- سوییچ قابل‌مشاهده در UI، بدون دانلود مجدد، با rollback در خطا؛ تداخل هم‌زمان Agent صفر.
- benchmark suite ثابت و real-world، رسید هر اجرا با device/model hash/runtime version و نتایج خام؛ گزارش خودکار مقایسه و رتبه‌بندی برای هر سناریو.
- تست مدل‌های سنگین با نتیجهٔ علمی حتی در صورت `NOT_FEASIBLE`؛ بدون freeze عمدی دستگاه.
- اجرای آفلاین بعد از جدا شدن USB و قطع Wi-Fi برای قابلیت‌های محلی؛ صرفاً Search اینترنت می‌خواهد.
- دستیار فارسی دوطرفه با مدیریت turn، TTS، مشاهدهٔ درخواست‌محور صفحه و خروجی سرچِ دارای منابع؛ اعمال حساس فقط با تأیید و allowlist.

## 9. منابع اولیهٔ معتبر (بررسی در 2026-10-15)

- Qwen3-ASR 0.6B: https://k2-fsa.github.io/sherpa/onnx/qwen3-asr/pretrained.html
- OmniASR ONNX: https://k2-fsa.github.io/sherpa/onnx/omnilingual-asr/models.html
- whisper.cpp مدل‌ها: https://github.com/ggml-org/whisper.cpp/tree/master/models
- Qwen3.5-2B: https://huggingface.co/Qwen/Qwen3.5-2B
- Qwen3.5-4B: https://huggingface.co/Qwen/Qwen3.5-4B
- Qwen3.5-9B: https://huggingface.co/Qwen/Qwen3.5-9B
- Qwen3-VL-2B: https://huggingface.co/Qwen/Qwen3-VL-2B-Instruct
- Gemma 3n (gated): https://huggingface.co/google/gemma-3n-E4B-it-litert-preview
- Piper فارسی: https://huggingface.co/rhasspy/piper-voices/tree/main/fa/fa_IR
- MMS Persian (noncommercial): https://huggingface.co/facebook/mms-tts-fas
- llama.cpp Android cross-build: https://github.com/ggml-org/llama.cpp/blob/master/docs/build.md

اندازه‌ها، مجوزها و فرمت‌های منتشرشده ممکن است با revision تغییر کنند؛ آنها را در زمان **انتخاب artifact واقعی** به manifest قفل می‌کنیم. این سند برنامهٔ اجراست، نه ادعای نصب ۳۱ مدل یا تضمین پشتیبانی همهٔ Runtimeها.
