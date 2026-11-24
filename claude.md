# MediDiary — Engineering Specification & Claude Code Guidelines

## 1. Project Summary & Guarantees
MediDiary is a 100% offline, privacy-first, bilingual (Bangla + English) Android health diary. It turns everyday spoken voice check-ins into structured, patient-verified timelines of medicines taken and symptoms experienced.

### Non-Negotiable Core Rules:
1. **Zero Cloud Inference:** No network permissions (`android.permission.INTERNET` MUST NOT be in AndroidManifest.xml). Works in Airplane Mode.
2. **Human-in-the-Loop:** AI proposals NEVER auto-save to the database. The user MUST explicitly verify or edit records on the review screen.
3. **No Clinical Advice:** No dosages, interactions, or diagnostic claims. Label all exports as "Patient-Reported & AI-Assisted, Not Medically Verified."
4. **Deterministic Generics:** The on-device LLM extracts `name`, `time_phrase`, and `note`. Generic medicine suggestions are matched deterministically via an on-device local DGDA alias SQLite table, NEVER hallucinated by the LLM.

---

## 2. Technical Stack
- **OS:** Android 8.0+ (API 26+), Target SDK 34
- **Language:** Kotlin 1.9+, Java 17
- **UI:** Jetpack Compose, Material 3, Navigation Compose
- **Concurrency:** Kotlin Coroutines & Flows
- **Database:** Android Room with SQLite (bundled DGDA pre-populated FTS table)
- **Local ASR:** `whisper.cpp` Android JNI (Whisper tiny/base)
- **Local Extraction:** `llama.cpp` Android JNI running fine-tuned `gemma-3-1b-it.Q4_K_M.gguf` (~0.81 GB)
- **Exporting:** Android native `PdfDocument` and CSV string builder

---

## 3. Data Architectures & Schemas

### 3.1 LLM JSON Output Schema
The fine-tuned model strictly outputs raw JSON without markdown formatting:
```json
{
  "records": [
    {
      "type": "medicine" | "symptom",
      "name": "string",
      "generic_suggestion": null,
      "time_phrase": "string or null",
      "note": "string or null"
    }
  ]
}
```

### 3.2 System Prompt (Exact String)
```text
Extract medicine and symptom records from the transcript into strict JSON. Output only JSON with schema: {"records": [{"type": "medicine"|"symptom", "name": string, "generic_suggestion": null, "time_phrase": string|null, "note": string|null}]}. Keep unknown fields null. Never infer dose, strength, frequency, or missing time.
```

### 3.3 Room Database Schema (`AppDatabase.kt`)

```kotlin
@Entity(tableName = "diary_sessions")
data class DiarySession(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val rawTranscript: String,
    val isVerified: Boolean = false
)

@Entity(
    tableName = "diary_records",
    foreignKeys = [
        ForeignKey(
            entity = DiarySession::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class DiaryRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val type: String, // "medicine" or "symptom"
    val originalName: String,
    val genericSuggestion: String? = null,
    val genericMatchStatus: String = "UNMATCHED", // "EXACT", "FUZZY", "UNMATCHED", "MANUAL"
    val timePhrase: String? = null,
    val note: String? = null,
    val userConfirmed: Boolean = true,
    val loggedDate: Long = System.currentTimeMillis()
)

@Entity(tableName = "dgda_medicines")
data class DgdaMedicine(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "brand_name") val brandName: String,
    @ColumnInfo(name = "generic_name") val genericName: String,
    @ColumnInfo(name = "dosage_form") val dosageForm: String? = null
)
```

---

## 4. Pipeline & Native Execution Flow

### 4.1 Sequential Memory Manager (Prevents Android OOM)
To ensure the app never exceeds the 1.5 GB RAM threshold on lower-end devices:
1. **Phase 1: Audio Recording:** Record PCM 16kHz 16-bit mono WAV into app-private cache (`context.cacheDir`).
2. **Phase 2: Speech-to-Text:** Load `whisper.cpp` model $\rightarrow$ Transcribe $\rightarrow$ Emit Transcript $\rightarrow$ Free Whisper JNI context.
3. **Phase 3: Human Edit:** User reviews and edits raw transcript on screen.
4. **Phase 4: Extraction:** Load `llama.cpp` model with `gemma-3-1b-it.Q4_K_M.gguf` (context 512, temp 0.0) $\rightarrow$ Infer JSON $\rightarrow$ Free/Pause Llama context.
5. **Phase 5: Sanitization & Lookup:** Kotlin rules sanitize output $\rightarrow$ Query DGDA SQLite table for `generic_suggestion`.
6. **Phase 6: Verification & Save:** User verifies cards $\rightarrow$ Write to Room $\rightarrow$ Delete temporary audio WAV file.

---

## 5. Defensive Sanitization Layer (`ExtractionSanitizer.kt`)
Before feeding LLM JSON to the UI, filter the array through this defensive sanitizer:

```kotlin
object ExtractionSanitizer {
    private val SCRIPT_REGEX = Regex("^[\\u0980-\\u09FFa-zA-Z0-9\\s\\-\\+\\.]+$")
    private val PLACEHOLDER_BLACKLIST = setOf(
        "কিছু না", "কিছু নেই", "কোনো কিছু না", "none", "nothing", 
        "no medicine", "na", "null", "ঔষধ", "ওষুধ"
    )
    private val INVALID_SYMPTOM_WORDS = setOf("ভাত", "খাবার", "পানি", "রুটি")

    fun sanitize(records: List<ProposedRecord>): List<ProposedRecord> {
        return records.filter { rec ->
            val name = rec.name.trim().lowercase()
            
            // 1. Drop blacklisted placeholders & hallucinations
            if (PLACEHOLDER_BLACKLIST.contains(name)) return@filter false
            
            // 2. Reject foreign scripts (e.g. Chinese tokens like "药")
            if (!SCRIPT_REGEX.matches(rec.name)) return@filter false
            
            // 3. Prevent food phrases misclassified as symptoms
            if (rec.type == "symptom" && INVALID_SYMPTOM_WORDS.any { name.contains(it) }) return@filter false
            
            // 4. Must have a valid type
            if (rec.type != "medicine" && rec.type != "symptom") return@filter false

            true
        }
    }
}
```

---

## 6. Brand-to-Generic Local Matcher (`DgdaLookupRepository.kt`)
```kotlin
class DgdaLookupRepository(private val db: AppDatabase) {
    suspend fun resolveGeneric(brandSpoken: String): Pair<String?, String> {
        val normalized = brandSpoken.trim().lowercase()
        
        // Exact match
        val exact = db.dgdaDao().findByBrandExact(normalized)
        if (exact != null) return Pair(exact.genericName, "EXACT")
        
        // Prefix/LIKE match
        val partial = db.dgdaDao().findByBrandLike("$normalized%")
        if (partial.isNotEmpty()) return Pair(partial.first().genericName, "FUZZY")
        
        return Pair(null, "UNMATCHED")
    }
}
```

---

## 7. User Interface & Screen Specifications

### Screen 1: Daily Check-In (`CheckInScreen.kt`)
- Big microphone button with animated pulse ring during audio capture.
- Time elapsed counter (records up to 60 seconds).
- "Type instead" and quick-entry chips (e.g., "Napa", "Paracetamol", "Gas", "Headache").
- Real-time amplitude visualizer.

### Screen 2: Transcript Review (`TranscriptScreen.kt`)
- Editable `OutlinedTextField` showing Whisper's output.
- Notice: *"You can fix any misheard medicine names before AI extraction."*
- Primary Button: "Process & Extract" $\rightarrow$ triggers Gemma inference.

### Screen 3: Verification & Approval (`ReviewScreen.kt`)
- Display cards categorized into two sections: **Medicines** (Blue accent) and **Symptoms** (Amber accent).
- Each Card contains:
  - Editable Name
  - Spoken Time phrase chip (e.g., "সকালে", "before lunch")
  - Optional note / dose phrase chip
  - Generic suggestion pill with indicator: `[DGDA Match: Paracetamol]` or `[Unmatched Brand]`
  - Delete (trash) icon and Edit icon.
- Floating Action Button: **"Confirm & Save to Diary"** (Persists only checked items to Room).

### Screen 4: Timeline & History (`TimelineScreen.kt`)
- Date picker filter (Today, Last 7 Days, Custom Date Range).
- Filter chips: `All`, `Medicines Only`, `Symptoms Only`.
- Grouped by date in reverse chronological order.
- Action bar icons: `Export PDF`, `Export CSV`.

### Screen 5: Export Generator (`ExportManager.kt`)
- **PDF Layout:** Clean one-page clinical report with:
  - Header: "MediDiary — Patient Reported Health Log"
  - Subheader: "Notice: Patient-entered, AI-assisted timeline. Not verified clinical diagnoses."
  - Patient metadata and Date range.
  - Table: `Date | Time Reported | Entry Type | Reported Name | Generic Match (DGDA) | Notes`
- **CSV Output:** Comma-separated UTF-8 values for easy spreadsheet analysis.

---

## 8. Step-by-Step Build Order for Claude Code

When prompting Claude Code to build this repository, follow this execution sequence:

1. **Step 1: Setup Architecture & Gradle**
   - Configure dependencies: Jetpack Compose, Room (with KSP), Navigation, Coroutines, Accompanist permissions.
   - Configure `CMakeLists.txt` for NDK, link `libwhisper.so` and `libllama.so`.

2. **Step 2: Room Database & DGDA Asset**
   - Create Room entities, DAOs, and migrations.
   - Place `dgda_reference.db` into `app/src/main/assets/databases/` (populated with standard BD drugs like Napa, Seclo, Sergel, Monas, Ace, Bizoran, etc.).

3. **Step 3: Native Inference Wrapper**
   - Implement `WhisperEngine.kt` and `GemmaEngine.kt` with JNI bridges.
   - Implement model file copying from assets/storage to `context.filesDir`.

4. **Step 4: Repository & Logic Layer**
   - Implement `ExtractionSanitizer.kt` and `DgdaLookupRepository.kt`.
   - Implement `DiaryRepository.kt` managing sessions and records.

5. **Step 5: Compose Screens**
   - Implement `CheckInScreen`, `TranscriptScreen`, `ReviewScreen`, and `TimelineScreen`.
   - Wire them into `MediDiaryNavHost.kt`.

6. **Step 6: PDF & CSV Generation**
   - Implement `PdfReportGenerator.kt` using Android's native `android.graphics.pdf.PdfDocument`.
   - Add FileProvider in `AndroidManifest.xml` for offline PDF sharing.
```
