package com.example.utils

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val TAG = "GeminiService"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Converts a list of Bitmaps and a prompt, sends them to Gemini 3.5 Flash, 
     * and returns the generated study/revision notes.
     */
    suspend fun generateNotesFromPages(
        bitmaps: List<Bitmap>,
        prompt: String = "You are a helpful study companion. Analyze these loaded PDF document page scans and write comprehensive, structured revision notes in markdown format. Focus purely on key concepts, formulas, definitions, and concise summaries. Do not include any polite conversational filler or introductions; begin directly with the study notes."
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API Key is missing or default placeholder.")
            return@withContext "Error: Gemini API Key is missing. Please add your GEMINI_API_KEY in the Secrets panel in AI Studio."
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            
            // Build the JSON payload using standard Android JSONObject
            val partsArray = JSONArray()
            
            // Add Prompt text part
            val textPart = JSONObject().apply {
                put("text", prompt)
            }
            partsArray.put(textPart)

            // Convert and add image parts
            for (bitmap in bitmaps) {
                val base64Image = bitmapToBase64(bitmap)
                val inlineDataObj = JSONObject().apply {
                    put("mimeType", "image/jpeg")
                    put("data", base64Image)
                }
                val imagePart = JSONObject().apply {
                    put("inlineData", inlineDataObj)
                }
                partsArray.put(imagePart)
            }

            val contentObj = JSONObject().apply {
                put("parts", partsArray)
            }

            val contentsArray = JSONArray().apply {
                put(contentObj)
            }

            val requestJson = JSONObject().apply {
                put("contents", contentsArray)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e(TAG, "API call failed with code ${response.code}: $errBody")
                    return@withContext "Error compiling study notes: API returned status ${response.code}.\nDetails: $errBody"
                }

                val resBodyStr = response.body?.string()
                if (resBodyStr.isNullOrEmpty()) {
                    return@withContext "Error: Empty response from AI service."
                }

                val jsonResponse = JSONObject(resBodyStr)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    return@withContext "Error: No candidates returned in AI response."
                }

                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content")
                if (content == null) {
                    return@withContext "Error: Missing content in candidate."
                }

                val parts = content.optJSONArray("parts")
                if (parts == null || parts.length() == 0) {
                    return@withContext "Error: Missing parts in respond content."
                }

                val text = parts.getJSONObject(0).optString("text")
                if (text.isEmpty()) {
                    return@withContext "Error: Generated text is empty."
                }

                return@withContext text
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating content from Gemini API", e)
            return@withContext "Failed to generate notes: ${e.localizedMessage ?: e.message}"
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // Compress page bitmap to a balance of quality and size
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
