package com.example.data.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

data class FaceAnalysisResult(
    val personName: String?,
    val description: String,
    val detectedFace: Boolean,
    val attire: String? = null,
    val environment: String? = null,
    val lighting: String? = null
)

object GeminiAnalyzer {
    private const val TAG = "GeminiAnalyzer"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun analyzePhoto(
        context: Context,
        resourceId: Int,
        resourceName: String,
        title: String
    ): FaceAnalysisResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        
        // 1. Check if we have a valid key. If placeholder or empty, do a local simulation.
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || apiKey.contains("PLACEHOLDER")) {
            Log.d(TAG, "No valid Gemini API key found. Using local mock analysis for $resourceName.")
            return@withContext simulateAnalysis(resourceName, title)
        }

        try {
            // 2. Load and compress Bitmap
            val bitmap = BitmapFactory.decodeResource(context.resources, resourceId)
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode resource $resourceName")
                return@withContext simulateAnalysis(resourceName, title)
            }
            
            // Resize to max 400px to save bandwidth and fit within token limits
            val scaledBitmap = scaleBitmapDown(bitmap, 400)
            val base64Image = bitmapToBase64(scaledBitmap)
            
            // 3. Prepare Prompt
            val prompt = """
                Analyze this photo. Return a JSON object with this exact format:
                {
                  "personName": "Alice" | "Bob" | "Clara" | "Unknown",
                  "detectedFace": true | false,
                  "description": "A brief, elegant 1-sentence description of the image.",
                  "attire": "A 2-4 word description of the person's clothing style and colors (e.g. 'Formal dark blazer', 'White Shirt & Glasses', 'Black Casual T-Shirt', 'Casual Sportswear'). Set to null if no person is detected.",
                  "environment": "A 2-4 word description of the scene background/location (e.g. 'Outdoor Sunny Park', 'Professional Media Studio', 'Indoor Cozy Living Room', 'Modern Office Conference Room'). Set to null if background is plain or undetectable.",
                  "lighting": "A 2-3 word description of the lighting condition (e.g. 'Natural Bright Daylight', 'Cool Ring Lighting', 'Warm Ambient Light', 'Cool Fluorescent Overhead Light'). Set to null if undetectable."
                }
                
                Matching guidelines for face identification:
                - If the person has stylish glasses, wavy light-brown hair, and outdoor natural light, name them "Alice".
                - If the person has a short dark beard and dark curly hair, name them "Bob".
                - If the person has short silver/grey hair, a warm smile, and a blue scarf, name them "Clara".
                - If no face is visible, or it doesn't match these three, set "detectedFace" to false and "personName" to "Unknown".
            """.trimIndent()

            // 4. Build JSON request body using standard org.json
            val inlineDataJson = JSONObject().apply {
                put("mimeType", "image/jpeg")
                put("data", base64Image)
            }
            
            val textPart = JSONObject().apply {
                put("text", prompt)
            }
            
            val imagePart = JSONObject().apply {
                put("inlineData", inlineDataJson)
            }
            
            val partsArray = JSONArray().apply {
                put(textPart)
                put(imagePart)
            }
            
            val contentJson = JSONObject().apply {
                put("parts", partsArray)
            }
            
            val contentsArray = JSONArray().apply {
                put(contentJson)
            }
            
            // Optional: responseMimeType in generationConfig
            val generationConfig = JSONObject().apply {
                put("responseMimeType", "application/json")
            }
            
            val requestBodyJson = JSONObject().apply {
                put("contents", contentsArray)
                put("generationConfig", generationConfig)
            }

            // 5. Execute Request
            val requestBody = requestBodyJson.toString().toRequestBody("application/json".toMediaType())
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image:generateContent?key=$apiKey"
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "API request failed with code ${response.code}: ${response.message}")
                    return@withContext simulateAnalysis(resourceName, title)
                }
                
                val responseBody = response.body?.string()
                if (responseBody == null) {
                    Log.e(TAG, "Response body is null")
                    return@withContext simulateAnalysis(resourceName, title)
                }

                // 6. Parse Response
                val rootJson = JSONObject(responseBody)
                val candidates = rootJson.getJSONArray("candidates")
                val firstCandidate = candidates.getJSONObject(0)
                val text = firstCandidate.getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                // Extract and clean JSON block if model wrapped it in markdown
                val cleanedJsonStr = text.substringAfter("```json").substringAfter("```").substringBeforeLast("```").trim()
                val resultJson = JSONObject(cleanedJsonStr)
                
                val personName = resultJson.optString("personName", "Unknown").let {
                    if (it == "Unknown" || it.isEmpty()) null else it
                }
                val detectedFace = resultJson.optBoolean("detectedFace", false)
                val description = resultJson.optString("description", "A beautiful gallery photo.")
                val attire = resultJson.optString("attire", "Unknown").let { if (it == "null" || it.isEmpty()) null else it }
                val environment = resultJson.optString("environment", "Unknown").let { if (it == "null" || it.isEmpty()) null else it }
                val lighting = resultJson.optString("lighting", "Unknown").let { if (it == "null" || it.isEmpty()) null else it }

                Log.d(TAG, "Gemini analysis success! Person: $personName, Face: $detectedFace, Attire: $attire, Env: $environment, Light: $lighting")
                FaceAnalysisResult(personName, description, detectedFace, attire, environment, lighting)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini analysis threw exception: ${e.message}", e)
            simulateAnalysis(resourceName, title)
        }
    }

    private fun simulateAnalysis(resourceName: String, title: String): FaceAnalysisResult {
        // Simulate local facial analysis based on resource names and titles
        val lowerResName = resourceName.lowercase()
        val lowerTitle = title.lowercase()
        
        return when {
            lowerResName.contains("alice") || lowerTitle.contains("alice") -> {
                when {
                    lowerTitle.contains("interview") || lowerTitle.contains("studio") -> {
                        FaceAnalysisResult(
                            personName = "Alice",
                            description = "Alice wearing a formal dark blazer in a professional media studio with cool ring lighting.",
                            detectedFace = true,
                            attire = "Formal Dark Blazer",
                            environment = "Professional Media Studio",
                            lighting = "Cool Ring Lighting"
                        )
                    }
                    lowerTitle.contains("jogging") || lowerTitle.contains("park") || lowerTitle.contains("run") -> {
                        FaceAnalysisResult(
                            personName = "Alice",
                            description = "Alice in sporty casual wear, jogging in an outdoor sunny park under natural daylight.",
                            detectedFace = true,
                            attire = "Casual Sportswear",
                            environment = "Outdoor Sunny Park",
                            lighting = "Natural Bright Daylight"
                        )
                    }
                    else -> {
                        FaceAnalysisResult(
                            personName = "Alice",
                            description = "Vibrant portrait of Alice with stylish glasses, light-brown wavy hair in outdoor natural light.",
                            detectedFace = true,
                            attire = "White Shirt & Glasses",
                            environment = "Outdoor Sunny Park",
                            lighting = "Natural Bright Daylight"
                        )
                    }
                }
            }
            lowerResName.contains("bob") || lowerTitle.contains("bob") -> {
                when {
                    lowerTitle.contains("office") || lowerTitle.contains("presentation") || lowerTitle.contains("meeting") -> {
                        FaceAnalysisResult(
                            personName = "Bob",
                            description = "Bob presenting confidently in a formal business suit at a modern office conference room.",
                            detectedFace = true,
                            attire = "Formal Business Suit",
                            environment = "Modern Office Conference Room",
                            lighting = "Cool Fluorescent Overhead Light"
                        )
                    }
                    else -> {
                        FaceAnalysisResult(
                            personName = "Bob",
                            description = "Close-up portrait of Bob smiling warmly with a short dark beard and dark curly hair in a cozy living room.",
                            detectedFace = true,
                            attire = "Black Casual T-Shirt",
                            environment = "Indoor Cozy Living Room",
                            lighting = "Warm Ambient Light"
                        )
                    }
                }
            }
            lowerResName.contains("clara") || lowerTitle.contains("clara") -> {
                FaceAnalysisResult(
                    personName = "Clara",
                    description = "Elegant portrait of Clara smiling with short silver hair and a lovely blue scarf in a misty autumn forest.",
                    detectedFace = true,
                    attire = "Blue Woolen Scarf",
                    environment = "Misty Autumn Forest",
                    lighting = "Overcast Soft Daylight"
                )
            }
            lowerResName.contains("mountain") || lowerTitle.contains("mountain") -> {
                FaceAnalysisResult(
                    personName = null,
                    description = "Spectacular view of snow-capped mountain peaks bathed in warm golden sunset light.",
                    detectedFace = false,
                    attire = null,
                    environment = "Mountain Peak",
                    lighting = "Golden Hour Sunset"
                )
            }
            lowerResName.contains("lake") || lowerTitle.contains("lake") -> {
                FaceAnalysisResult(
                    personName = null,
                    description = "Serene alpine lake reflecting dense pine forests under a misty morning sky.",
                    detectedFace = false,
                    attire = null,
                    environment = "Alpine Lake",
                    lighting = "Overcast Soft Daylight"
                )
            }
            else -> {
                FaceAnalysisResult(
                    personName = null,
                    description = "A clean digital capture stored in the media catalog.",
                    detectedFace = false,
                    attire = null,
                    environment = "Unknown Setting",
                    lighting = "Ambient Light"
                )
            }
        }
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        var newWidth = originalWidth
        var newHeight = originalHeight

        if (originalWidth > originalHeight) {
            if (originalWidth > maxDimension) {
                newWidth = maxDimension
                newHeight = (newWidth * originalHeight) / originalWidth
            }
        } else {
            if (originalHeight > maxDimension) {
                newHeight = maxDimension
                newWidth = (newHeight * originalWidth) / originalHeight
            }
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
