package com.example.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.AiCreatorPlanResult
import com.example.data.model.AiEditSuggestion
import com.example.data.model.AiScriptResult
import com.example.data.model.AiVideoAnalysisResult
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiCreatorRepo"

class GeminiCreatorRepository {

    private val scriptSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Catchy short-form video title"),
            "hook" to Schema.string(description = "Opening 2-second visual and spoken hook"),
            "narration" to Schema.string(description = "Full spoken narration script broken into clear paragraphs"),
            "sceneDescriptions" to Schema.array(
                items = Schema.string(),
                description = "3 to 5 sequential visual scene descriptions matching the narration"
            ),
            "cta" to Schema.string(description = "Strong closing call to action")
        )
    )

    private val videoAnalysisSchema = Schema.obj(
        properties = mapOf(
            "summary" to Schema.string(description = "Brief editorial assessment of the imported video"),
            "suggestions" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "category" to Schema.enumeration(
                            values = listOf("Cut", "Caption", "Music", "Transition"),
                            description = "Editing action type"
                        ),
                        "title" to Schema.string(description = "Short actionable recommendation title"),
                        "detail" to Schema.string(description = "Specific explanation and creator rationale"),
                        "startMs" to Schema.integer(description = "Start timestamp in milliseconds"),
                        "endMs" to Schema.integer(description = "End timestamp in milliseconds")
                    )
                ),
                description = "4 to 6 concrete editing recommendations"
            )
        )
    )

    private val creatorPlanSchema = Schema.obj(
        properties = mapOf(
            "titles" to Schema.array(
                items = Schema.string(),
                description = "3 high-CTR title options for Reels and YouTube Shorts"
            ),
            "description" to Schema.string(description = "Optimized short-form video description"),
            "hashtags" to Schema.array(
                items = Schema.string(),
                description = "8 relevant hashtags including # prefix"
            ),
            "improvements" to Schema.array(
                items = Schema.string(),
                description = "4 practical retention and pacing improvement tips"
            )
        )
    )

    private val scriptModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = scriptSchema
            }
        )
    }

    private val analysisModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = videoAnalysisSchema
            }
        )
    }

    private val creatorPlanModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = creatorPlanSchema
            }
        )
    }

    private val textModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    suspend fun generateScript(
        topic: String,
        category: String,
        language: String,
        durationSeconds: Int
    ): AiScriptResult {
        val prompt = """
            You are an expert short-form video scriptwriter for Instagram Reels and YouTube Shorts.
            Write an engaging $durationSeconds-second script in $language.
            Category: $category
            Topic: $topic
            Language rules:
            - If English: natural punchy creator English.
            - If Hindi: clean Devanagari Hindi suitable for voiceover.
            - If Hinglish: conversational Indian creator Hinglish written cleanly in Roman script.
            Provide a strong hook, full spoken narration split into 3-5 paragraphs, matching scene descriptions, and a concise CTA.
        """.trimIndent()

        return runAiCall("Script generation") {
            val response = withTimeout(60_000L) { scriptModel.generateContent(prompt) }
            val jsonStr = response.text ?: throw IllegalStateException("Empty response from Gemini.")
            val obj = JSONObject(jsonStr)
            val scenesArr = obj.getJSONArray("sceneDescriptions")
            val scenes = (0 until scenesArr.length()).map { scenesArr.getString(it) }
            AiScriptResult(
                title = obj.getString("title"),
                hook = obj.getString("hook"),
                narration = obj.getString("narration"),
                sceneDescriptions = scenes,
                cta = obj.getString("cta")
            )
        }
    }

    suspend fun analyzeVideoForEdits(
        keyframes: List<Bitmap>,
        durationMs: Long,
        aspectInfo: String,
        creatorGoal: String
    ): AiVideoAnalysisResult {
        return runAiCall("Video analysis") {
            val response = withTimeout(60_000L) {
                analysisModel.generateContent(
                    content {
                        text(
                            """
                            Analyze these ${keyframes.size} sequential keyframes from a ${durationMs}ms video ($aspectInfo).
                            Creator editing goal: ${creatorGoal.ifBlank { "High-retention Reels/Shorts edit" }}.
                            Suggest 4 to 6 concrete editing actions across Cut, Caption, Music, and Transition.
                            Ensure startMs and endMs stay within 0..$durationMs.
                            """.trimIndent()
                        )
                        keyframes.forEach { image(it) }
                    }
                )
            }
            val jsonStr = response.text ?: throw IllegalStateException("Empty analysis response.")
            val obj = JSONObject(jsonStr)
            val arr = obj.getJSONArray("suggestions")
            val suggestions = (0 until arr.length()).map { i ->
                val item = arr.getJSONObject(i)
                val start = item.optLong("startMs", 0L).coerceIn(0L, durationMs)
                val end = item.optLong("endMs", (start + 2000L).coerceAtMost(durationMs)).coerceIn(start, durationMs)
                AiEditSuggestion(
                    category = item.getString("category"),
                    title = item.getString("title"),
                    detail = item.getString("detail"),
                    startMs = start,
                    endMs = end,
                    approved = false
                )
            }
            AiVideoAnalysisResult(
                summary = obj.getString("summary"),
                suggestions = suggestions
            )
        }
    }

    suspend fun generateCreatorPlan(
        ideaOrTranscript: String,
        platform: String,
        language: String
    ): AiCreatorPlanResult {
        val prompt = """
            Act as a growth strategist for $platform creators.
            Language: $language
            Video concept or transcript: $ideaOrTranscript
            Generate 3 clickable titles, an engaging description, 8 targeted hashtags, and 4 actionable retention improvements.
        """.trimIndent()

        return runAiCall("Creator planning") {
            val response = withTimeout(60_000L) { creatorPlanModel.generateContent(prompt) }
            val jsonStr = response.text ?: throw IllegalStateException("Empty creator plan response.")
            val obj = JSONObject(jsonStr)
            val titlesArr = obj.getJSONArray("titles")
            val hashtagsArr = obj.getJSONArray("hashtags")
            val tipsArr = obj.getJSONArray("improvements")
            AiCreatorPlanResult(
                titles = (0 until titlesArr.length()).map { titlesArr.getString(it) },
                description = obj.getString("description"),
                hashtags = (0 until hashtagsArr.length()).map { hashtagsArr.getString(it) },
                improvements = (0 until tipsArr.length()).map { tipsArr.getString(it) }
            )
        }
    }

    suspend fun polishCaptionsToHinglish(rawLines: List<String>): List<String> {
        if (rawLines.isEmpty()) return emptyList()
        val joined = rawLines.joinToString("\n")
        return runAiCall("Caption Hinglish polish") {
            val response = withTimeout(60_000L) {
                textModel.generateContent(
                    "Convert and clean punctuation for these short video caption lines into natural Hinglish (Roman script, keep exact same number of lines, one per line, no numbering):\n$joined"
                )
            }
            val lines = response.text.orEmpty()
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (lines.size == rawLines.size) lines else rawLines
        }
    }

    private suspend inline fun <T> runAiCall(operationName: String, block: () -> T): T {
        return try {
            block()
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "$operationName timed out", e)
            throw IllegalStateException("$operationName timed out after 60 seconds. Check your connection and try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$operationName failed", e)
            val msg = e.localizedMessage ?: "Gemini request failed."
            throw IllegalStateException(
                "$operationName failed: $msg (Requires active internet and Firebase AI Logic quota.)",
                e
            )
        }
    }
}
