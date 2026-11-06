package com.evyr.rads.data.remote

import android.util.Base64
import com.evyr.rads.data.ScannedFood
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Sends a photo of a plate or menu to Gemini and asks for structured macros.
 * Optional — barcode scanning works without any of this.
 */
object GeminiVision {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    sealed class Result {
        data class Found(val foods: List<ScannedFood>) : Result()
        data class Failed(val message: String) : Result()
        /** The model name is dead; the API suggested this one instead. */
        data class ModelRetired(val suggested: String) : Result()
    }

    private const val TEXT_PROMPT = """
You are a nutrition estimator with good knowledge of US chain restaurant menus.

The user searched for a food that is not in the USDA or Open Food Facts
databases. Identify the most likely item(s) and give the nutrition for the
standard portion the restaurant or recipe actually serves.

Respond with ONLY a JSON array, no markdown fences, no commentary:
[
  {
    "name": "brand and item name",
    "calories": 0,
    "fat_g": 0,
    "saturated_fat_g": 0,
    "protein_g": 0,
    "carbs_g": 0,
    "sugar_g": 0,
    "fiber_g": 0,
    "sodium_mg": 0,
    "tags": [],
    "portion": "the portion these numbers describe, e.g. 1 biscuit",
    "confidence": "high" | "medium" | "low"
  }
]

Rules:
- Use the chain's own published nutrition where you know it.
- "tags" lists which of these apply, using only these exact words:
  caffeine, alcohol, spicy, fried, acidic, carbonated, chocolate_mint, dairy,
  gluten, high_purine, high_fodmap, fructose. Include things that are typical
  of how the item is actually made, e.g. a restaurant chicken biscuit is fried
  and contains gluten and dairy. Use [] if none apply.
- Numbers are for ONE standard portion, not per 100 g.
- At most 5 entries, best match first.
- If you have no idea, return [].
"""

    private const val PROMPT = """
You are a nutrition estimator. Look at the image and identify the food items.

If it is a MENU, list the dishes shown and estimate typical restaurant portions.
If it is a PLATE or PACKAGE, estimate what is actually present.

Respond with ONLY a JSON array, no markdown fences, no commentary:
[
  {
    "name": "short dish name",
    "calories": 0,
    "fat_g": 0,
    "saturated_fat_g": 0,
    "protein_g": 0,
    "carbs_g": 0,
    "sugar_g": 0,
    "fiber_g": 0,
    "sodium_mg": 0,
    "tags": [],
    "portion": "what portion this assumes",
    "confidence": "high" | "medium" | "low"
  }
]

Rules:
- Numbers only, no units inside the numeric fields.
- "tags" lists which of these apply, using only these exact words:
  caffeine, alcohol, spicy, fried, acidic, carbonated, chocolate_mint, dairy,
  gluten, high_purine, high_fodmap, fructose. Include things that are typical
  of how the item is actually made, e.g. a restaurant chicken biscuit is fried
  and contains gluten and dairy. Use [] if none apply.
- Estimate one entry per distinct dish, at most 8 entries.
- If you cannot identify any food, return [].
"""

    private const val MENU_PROMPT = """
You are a nutrition estimator reading a RESTAURANT MENU photo.

List every dish you can read, up to 15, and estimate each for the standard
portion the restaurant serves. Use the dish name and description, and allow
for how it is cooked (frying, butter, cream, cheese, sauces, dressings).

Respond with ONLY a JSON array, no markdown fences, no commentary:
[
  {
    "name": "dish name as printed",
    "calories": 0,
    "fat_g": 0,
    "saturated_fat_g": 0,
    "protein_g": 0,
    "carbs_g": 0,
    "sugar_g": 0,
    "fiber_g": 0,
    "sodium_mg": 0,
    "tags": [],
    "portion": "what portion this assumes",
    "confidence": "high" | "medium" | "low"
  }
]

Rules:
- Numbers only, no units inside the numeric fields.
- "tags" lists which of these apply, using only these exact words:
  caffeine, alcohol, spicy, fried, acidic, carbonated, chocolate_mint, dairy,
  gluten, high_purine, high_fodmap, fructose. Use [] if none apply.
- One entry per distinct dish. Skip drinks unless they are the point of the menu.
- If the photo is not a menu or you cannot read it, return [].
"""

    /** Text fallback for items neither database carries. */
    suspend fun estimateFromText(
        apiKey: String,
        model: String,
        query: String
    ): Result = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.Failed("No API key set.")
        try {
            val payload = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray()
                        .put(JSONObject().put("text", TEXT_PROMPT))
                        .put(JSONObject().put("text", "Search term: $query"))
                    )
                ))
                put("generationConfig", JSONObject().put("temperature", 0.2))
            }
            request(apiKey, model, payload)
        } catch (e: Exception) {
            Result.Failed(e.message ?: "Estimate failed.")
        }
    }

    suspend fun analyze(
        apiKey: String,
        model: String,
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        menu: Boolean = false
    ): Result = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.Failed("No API key set.")
        try {
            val b64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

            val payload = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray()
                        .put(JSONObject().put("text", if (menu) MENU_PROMPT else PROMPT))
                        .put(JSONObject().put("inline_data", JSONObject()
                            .put("mime_type", mimeType)
                            .put("data", b64)))
                    )
                ))
                put("generationConfig", JSONObject().put("temperature", 0.2))
            }

            request(apiKey, model, payload)
        } catch (e: Exception) {
            Result.Failed(e.message ?: "Vision error.")
        }
    }

    private fun request(apiKey: String, model: String, payload: JSONObject): Result {
        try {
            val url =
                "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

            val req = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val msg = runCatching {
                        JSONObject(body).optJSONObject("error")?.optString("message")
                    }.getOrNull()

                    // Google retires model names and names the replacement in
                    // the error text. Pull it out so the caller can retry.
                    val replacement = msg?.let { text ->
                        Regex("models/([a-zA-Z0-9._-]+)")
                            .findAll(text)
                            .map { it.groupValues[1] }
                            .firstOrNull { it != model }
                    }
                    if (replacement != null) {
                        return Result.ModelRetired(replacement)
                    }

                    return Result.Failed(
                        msg ?: "Request failed (${response.code})."
                    )
                }

                val text = runCatching {
                    JSONObject(body)
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                }.getOrNull() ?: return Result.Failed("Unexpected response shape.")

                val cleaned = text
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()

                val arr = runCatching { JSONArray(cleaned) }.getOrNull()
                    ?: return Result.Failed("Could not read the estimate.")

                if (arr.length() == 0) {
                    return Result.Failed("No match found.")
                }

                val foods = buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        add(
                            ScannedFood(
                                name = o.optString("name", "Unknown item"),
                                calories = o.optInt("calories", 0),
                                fatGrams = o.optDouble("fat_g", 0.0),
                                proteinGrams = o.optDouble("protein_g", 0.0),
                                carbGrams = o.optDouble("carbs_g", 0.0),
                                saturatedFatGrams =
                                    o.optDouble("saturated_fat_g", 0.0).takeIf { it > 0 },
                                sugarGrams = o.optDouble("sugar_g", Double.NaN).takeIf { !it.isNaN() },
                                fiberGrams = o.optDouble("fiber_g", Double.NaN).takeIf { !it.isNaN() },
                                sodiumMg = o.optDouble("sodium_mg", Double.NaN).takeIf { !it.isNaN() },
                                tags = o.optJSONArray("tags")?.let { arr ->
                                    (0 until arr.length()).mapNotNull {
                                        arr.optString(it).trim().lowercase().ifBlank { null }
                                    }.toSet()
                                } ?: emptySet(),
                                servingNote = o.optString("portion", "").ifBlank { null },
                                source = "vision",
                                confidence = o.optString("confidence", "medium")
                            )
                        )
                    }
                }
                return Result.Found(foods)
            }
        } catch (e: Exception) {
            return Result.Failed(e.message ?: "Request failed.")
        }
    }
}
