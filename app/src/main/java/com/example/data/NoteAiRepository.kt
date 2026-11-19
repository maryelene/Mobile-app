package com.example.data

import android.graphics.Bitmap
import android.util.Log
import com.example.model.ExtractedDefinitionSuggestion
import com.example.model.NoteSourceType
import com.example.model.PhotoExtractionResult
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class NoteAiRepository {
    private val extractionSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "A concise 2-6 word study title for the page"),
            "sourceType" to Schema.enumeration(
                values = listOf("PRINTED", "HANDWRITING"),
                description = "Whether the image shows a printed page/book or handwritten notes"
            ),
            "extractedText" to Schema.string(
                description = "Clean, accurate, editable plain text transcribed from the page, preserving paragraphs and bullet lists."
            ),
            "suggestedDefinitions" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "term" to Schema.string(description = "Key term, vocabulary word, or concept"),
                        "definition" to Schema.string(description = "Concise definition or explanation from the text")
                    )
                ),
                description = "Up to 8 key terms and definitions found on the page"
            )
        )
    )

    private val definitionsSchema = Schema.obj(
        properties = mapOf(
            "suggestedDefinitions" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "term" to Schema.string(description = "Key term or concept"),
                        "definition" to Schema.string(description = "Clear student-friendly definition based on the note")
                    )
                ),
                description = "Up to 8 key terms and definitions extracted from the note text"
            )
        )
    )

    private val extractionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = extractionSchema
            }
        )
    }

    private val definitionsModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = definitionsSchema
            }
        )
    }

    suspend fun extractTextFromPhoto(bitmap: Bitmap): Result<PhotoExtractionResult> {
        return try {
            val response = withTimeout(60_000L) {
                extractionModel.generateContent(
                    content {
                        image(bitmap)
                        text(
                            "Transcribe all readable text from this student study photo (printed textbook/handout or clean handwriting) into clean, editable plain text. " +
                                "Fix obvious line-break splits inside sentences while keeping headings and bullet points tidy. " +
                                "Also identify key terms and definitions present in the text for quick student highlighting."
                        )
                    }
                )
            }
            val jsonString = response.text
                ?: return Result.failure(IllegalStateException("AI returned an empty response."))
            val json = JSONObject(jsonString)
            val title = json.optString("title", "Scanned Study Note")
                .ifBlank { "Scanned Study Note" }
                .take(120)
            val sourceType = when (json.optString("sourceType", "PRINTED").uppercase()) {
                "HANDWRITING" -> NoteSourceType.HANDWRITING
                else -> NoteSourceType.PRINTED
            }
            val extractedText = json.optString("extractedText", "").take(20000)
            val suggestions = parseSuggestions(json)
            Result.success(
                PhotoExtractionResult(
                    title = title,
                    sourceType = sourceType,
                    extractedText = extractedText,
                    suggestedDefinitions = suggestions
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Photo text extraction timed out", e)
            Result.failure(Exception("Text extraction timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Photo text extraction failed", e)
            Result.failure(e)
        }
    }

    suspend fun suggestDefinitionsFromText(noteText: String): Result<List<ExtractedDefinitionSuggestion>> {
        if (noteText.isBlank()) {
            return Result.failure(IllegalArgumentException("Add some note text first before finding definitions."))
        }
        return try {
            val response = withTimeout(45_000L) {
                definitionsModel.generateContent(
                    "Identify up to 6 key terms and concise definitions from these student class notes:\n\n${noteText.take(12000)}"
                )
            }
            val jsonString = response.text
                ?: return Result.failure(IllegalStateException("AI returned an empty response."))
            val json = JSONObject(jsonString)
            Result.success(parseSuggestions(json))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Definition suggestion timed out", e)
            Result.failure(Exception("Finding definitions timed out. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Definition suggestion failed", e)
            Result.failure(e)
        }
    }

    private fun parseSuggestions(json: JSONObject): List<ExtractedDefinitionSuggestion> {
        val arr = json.optJSONArray("suggestedDefinitions") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val term = obj.optString("term", "").trim().take(120)
                val def = obj.optString("definition", "").trim().take(1000)
                if (term.isNotEmpty() && def.isNotEmpty()) {
                    add(ExtractedDefinitionSuggestion(term = term, definition = def))
                }
            }
        }
    }

    private companion object {
        const val TAG = "NoteAiRepo"
    }
}
