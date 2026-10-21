package com.example.ai

import android.util.Log
import com.example.BuildConfig
import com.example.data.PresetRepository
import com.example.model.FxModule
import com.example.model.PresetRig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class ToneGeneratorEngine {

    companion object {
        private const val TAG = "ToneGeneratorEngine"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun generatePreset(song: String, prompt: String, targetPatch: Int = 0): PresetRig = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }

        if (apiKey.isNotBlank() && !apiKey.contains("PLACEHOLDER", ignoreCase = true) && !apiKey.contains("MY_GEMINI_API_KEY", ignoreCase = true)) {
            try {
                val cloudPreset = callGeminiForPreset(apiKey, song, prompt, targetPatch)
                if (cloudPreset != null) {
                    return@withContext cloudPreset
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini generation fallback to local Tone Architect: ${e.message}")
            }
        }

        // Local algorithmic Tone Architect Engine
        return@withContext architectLocalPreset(song, prompt, targetPatch)
    }

    private fun callGeminiForPreset(apiKey: String, song: String, prompt: String, targetPatch: Int): PresetRig? {
        val systemPrompt = """
            You are a master guitar sound designer and Valeton GP-180 multi-effects rig architect.
            Create a realistic, dialed-in signal chain for Valeton GP-180 based on the song reference and sound character.
            Return ONLY a valid JSON object matching this schema:
            {
              "name": "Preset Name",
              "fs1Title": "FS1 Action (e.g. Toggle BST)",
              "fs2Title": "FS2 Action (e.g. Toggle DLY)",
              "fs3Title": "FS3 Action (e.g. Solo Boost)",
              "modules": [
                {
                  "id": "NR",
                  "name": "Noise Gate",
                  "category": "Gate",
                  "enabled": true,
                  "cc": 21,
                  "params": {"Threshold": 60, "Decay": 40}
                },
                {
                  "id": "BST",
                  "name": "Green OD",
                  "category": "Overdrive",
                  "enabled": true,
                  "cc": 22,
                  "params": {"Gain": 30, "Tone": 65, "Level": 80}
                },
                {
                  "id": "AMP",
                  "name": "Amp Model Name",
                  "category": "High Gain Amp",
                  "enabled": true,
                  "cc": 23,
                  "params": {"Gain": 85, "Bass": 60, "Mid": 30, "Treble": 75, "Presence": 70, "Master": 75}
                },
                {
                  "id": "CAB",
                  "name": "Cabinet IR Name",
                  "category": "Cabinet IR",
                  "enabled": true,
                  "cc": 24,
                  "params": {"Mic": 57, "Center": 40, "HiCut": 75, "LoCut": 20}
                },
                {
                  "id": "EQ",
                  "name": "Graphic EQ",
                  "category": "Equalizer",
                  "enabled": true,
                  "cc": 25,
                  "params": {"100Hz": 55, "400Hz": 40, "800Hz": 30, "1.6kHz": 55, "3.2kHz": 70}
                },
                {
                  "id": "MOD",
                  "name": "Modulation Name",
                  "category": "Modulation",
                  "enabled": false,
                  "cc": 29,
                  "params": {"Rate": 30, "Depth": 50, "Mix": 35}
                },
                {
                  "id": "DLY",
                  "name": "Delay Name",
                  "category": "Delay",
                  "enabled": true,
                  "cc": 26,
                  "params": {"Time": 40, "Feedback": 30, "Mix": 25}
                },
                {
                  "id": "RVB",
                  "name": "Reverb Name",
                  "category": "Reverb",
                  "enabled": true,
                  "cc": 27,
                  "params": {"Decay": 35, "PreDly": 15, "Mix": 20}
                }
              ]
            }
        """.trimIndent()

        val userContent = "Song: $song\nSound Description: $prompt"

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", "$systemPrompt\n\nRequest:\n$userContent"))
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.4)
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val responseStr = response.body?.string() ?: return null
            val root = JSONObject(responseStr)
            val candidates = root.optJSONArray("candidates") ?: return null
            val text = candidates.getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            val jsonPreset = JSONObject(text.trim())
            return parsePresetJson(jsonPreset, song, prompt, targetPatch)
        }
    }

    private fun parsePresetJson(json: JSONObject, song: String, prompt: String, targetPatch: Int): PresetRig {
        val name = json.optString("name", song.ifBlank { "AI Tone Rig" })
        val fs1Title = json.optString("fs1Title", "Toggle BST")
        val fs2Title = json.optString("fs2Title", "Toggle DLY")
        val fs3Title = json.optString("fs3Title", "Solo Boost (+3dB)")

        val modules = mutableListOf<FxModule>()
        val array = json.optJSONArray("modules")
        if (array != null) {
            for (i in 0 until array.length()) {
                val mObj = array.getJSONObject(i)
                val mId = mObj.optString("id", "MOD")
                val mName = mObj.optString("name", "Module")
                val mCat = mObj.optString("category", "FX")
                val mEnabled = mObj.optBoolean("enabled", true)
                val mCc = mObj.optInt("cc", 20 + i)

                val paramsMap = linkedMapOf<String, Int>()
                val pObj = mObj.optJSONObject("params")
                if (pObj != null) {
                    val keys = pObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        paramsMap[k] = pObj.optInt(k, 50).coerceIn(0, 100)
                    }
                }
                modules.add(FxModule(mId, mName, mCat, mEnabled, mCc, paramsMap))
            }
        }

        if (modules.isEmpty()) {
            modules.addAll(PresetRepository.getDefaultModules())
        }

        return PresetRig(
            id = "ai_" + UUID.randomUUID().toString().take(8),
            name = name,
            songReference = song,
            soundPrompt = prompt,
            patchNumber = targetPatch,
            modules = modules,
            fs1Title = fs1Title,
            fs1Cc = 81,
            fs2Title = fs2Title,
            fs2Cc = 82,
            fs3Title = fs3Title,
            fs3Cc = 83
        )
    }

    private fun architectLocalPreset(song: String, prompt: String, targetPatch: Int): PresetRig {
        val text = "$song $prompt".lowercase()

        val isHighGain = text.contains("metal") || text.contains("anger") || text.contains("chug") ||
                text.contains("djent") || text.contains("high gain") || text.contains("recto") || text.contains("heavy")
        val isAcousticClean = text.contains("clean") || text.contains("acoustic") || text.contains("jazz") ||
                text.contains("polyphia") || text.contains("nylon") || text.contains("sparkle")
        val isAmbient = text.contains("ambient") || text.contains("space") || text.contains("shimmer") ||
                text.contains("shoegaze") || text.contains("dream")
        val isVintageBlues = text.contains("blues") || text.contains("srv") || text.contains("hendrix") ||
                text.contains("plexi") || text.contains("mayer") || text.contains("crunch")

        val modules = mutableListOf<FxModule>()

        // 1. Noise Gate
        val gateThreshold = if (isHighGain) 72 else if (isAcousticClean) 30 else 50
        modules.add(
            FxModule("NR", "Noise Gate", "Gate", true, 21, linkedMapOf("Threshold" to gateThreshold, "Decay" to 35))
        )

        // 2. Booster / Drive
        val (bstName, bstGain, bstTone, bstLevel, bstOn) = when {
            isHighGain -> Quintuple("Green OD", 22, 70, 85, true)
            isAcousticClean -> Quintuple("Opto Compressor", 15, 60, 75, false)
            isVintageBlues -> Quintuple("Texas Overdrive", 55, 60, 75, true)
            isAmbient -> Quintuple("Warm Preamp", 35, 55, 80, true)
            else -> Quintuple("Golden Horse", 40, 65, 78, true)
        }
        modules.add(
            FxModule("BST", bstName, "Overdrive", bstOn, 22, linkedMapOf("Gain" to bstGain, "Tone" to bstTone, "Level" to bstLevel))
        )

        // 3. Amp Model
        val (ampName, ampCat, gain, bass, mid, treb, pres) = when {
            isHighGain -> Septuple("Cali Recto (NAM)", "High Gain Amp", 88, 62, 24, 76, 72)
            isAcousticClean -> Septuple("US Clean Twin 65", "Clean Amp", 24, 45, 65, 82, 78)
            isAmbient -> Septuple("UK Chime 30 TB", "Tube Combo", 42, 50, 60, 75, 70)
            isVintageBlues -> Septuple("Plexi 100 Vintage", "Vintage Amp", 72, 55, 75, 68, 70)
            else -> Septuple("Modern Rock 800", "Crunch Amp", 68, 58, 55, 70, 65)
        }
        modules.add(
            FxModule("AMP", ampName, ampCat, true, 23, linkedMapOf(
                "Gain" to gain, "Bass" to bass, "Mid" to mid, "Treble" to treb, "Presence" to pres, "Master" to 75
            ))
        )

        // 4. Cabinet IR
        val cabName = if (isHighGain) "V30 4x12 Recto" else if (isAcousticClean) "Open Back 2x12" else "Greenback 4x12"
        modules.add(
            FxModule("CAB", cabName, "Cabinet IR", true, 24, linkedMapOf("Mic" to 57, "Center" to 42, "HiCut" to 72, "LoCut" to 20))
        )

        // 5. EQ
        val (e100, e400, e800, e1k, e3k) = when {
            isHighGain -> Quintuple(58, 42, 28, 52, 72) // scooped mids
            isVintageBlues -> Quintuple(48, 55, 65, 60, 52) // mid hump
            isAcousticClean -> Quintuple(40, 48, 55, 65, 80) // sparkling highs
            else -> Quintuple(50, 50, 50, 50, 50)
        }
        modules.add(
            FxModule("EQ", "Graphic EQ", "Equalizer", true, 25, linkedMapOf(
                "100Hz" to e100, "400Hz" to e400, "800Hz" to e800, "1.6kHz" to e1k, "3.2kHz" to e3k
            ))
        )

        // 6. Modulation
        val (modName, modRate, modDepth, modMix, modOn) = when {
            isAmbient -> Quintuple("Dimension Chorus", 25, 65, 55, true)
            isVintageBlues -> Quintuple("Uni-Vibe", 38, 70, 40, false)
            isAcousticClean -> Quintuple("Subtle Chorus", 20, 35, 25, true)
            else -> Quintuple("Flanger Jet", 30, 45, 30, false)
        }
        modules.add(
            FxModule("MOD", modName, "Modulation", modOn, 29, linkedMapOf(
                "Rate" to modRate, "Depth" to modDepth, "Mix" to modMix
            ))
        )

        // 7. Delay
        val (dlyTime, dlyFeed, dlyMix, dlyOn) = when {
            isAmbient -> Quadruple(68, 55, 45, true)
            isHighGain -> Quadruple(35, 25, 20, false)
            isVintageBlues -> Quadruple(24, 18, 25, true) // slapback
            else -> Quadruple(45, 30, 25, true)
        }
        modules.add(
            FxModule("DLY", "Tape Echo", "Delay", dlyOn, 26, linkedMapOf(
                "Time" to dlyTime, "Feedback" to dlyFeed, "Mix" to dlyMix
            ))
        )

        // 8. Reverb
        val (rvbDecay, rvbPre, rvbMix) = when {
            isAmbient -> Triple(75, 30, 45)
            isHighGain -> Triple(28, 12, 16)
            isAcousticClean -> Triple(45, 18, 26)
            else -> Triple(35, 15, 22)
        }
        modules.add(
            FxModule("RVB", "Plate & Room", "Reverb", true, 27, linkedMapOf(
                "Decay" to rvbDecay, "PreDly" to rvbPre, "Mix" to rvbMix
            ))
        )

        val presetTitle = if (song.isNotBlank()) song else "Custom Tone Rig"
        return PresetRig(
            id = "ai_" + UUID.randomUUID().toString().take(8),
            name = presetTitle,
            songReference = song,
            soundPrompt = prompt,
            patchNumber = targetPatch,
            modules = modules,
            fs1Title = if (isHighGain) "Gate / OD" else "Toggle BST",
            fs1Cc = 81,
            fs2Title = if (isAmbient) "Hold Freeze" else "Toggle DLY",
            fs2Cc = 82,
            fs3Title = "Solo Boost (+3dB)",
            fs3Cc = 83
        )
    }

    // Helper data tuples
    private data class TripleVal<A, B, C>(val a: A, val b: B, val c: C)
    private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
    private data class Quintuple<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
    private data class Septuple<A, B, C, D, E, F, G>(val a: A, val b: B, val c: C, val d: D, val e: E, val f: F, val g: G)
}
