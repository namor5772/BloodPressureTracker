package au.roman.bloodpressuretracker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val API_URL = "https://api.anthropic.com/v1/messages"
private const val MODEL = "claude-haiku-4-5-20251001"
private const val MAX_TOKENS = 512

private const val SYSTEM_PROMPT =
    "You are a helpful health information assistant. Given a blood pressure reading, explain what " +
    "the numbers mean in plain language. Categorise the reading (normal, elevated, stage 1 hypertension, " +
    "stage 2 hypertension, or hypertensive crisis) using standard AHA/ACC guidelines. Keep the response " +
    "to 2 short paragraphs. The first paragraph discussing the blood pressure reading. The second paragraph " +
    "discussing the heart rate in context."

private const val DAILY_AVG_SYSTEM_PROMPT =
    "You are a helpful health information assistant. Given a daily average blood pressure reading " +
    "(averaged across multiple measurements taken on the same day), explain what the numbers mean in " +
    "plain language. Categorise the reading (normal, elevated, stage 1 hypertension, stage 2 hypertension, " +
    "or hypertensive crisis) using standard AHA/ACC guidelines. Note that daily averages smooth out " +
    "individual measurement variability and may be more representative of true blood pressure. Keep the " +
    "response to 2 short paragraphs. The first paragraph discussing the blood pressure reading. The second " +
    "paragraph discussing the heart rate in context."

suspend fun getBloodPressureExplanation(
    apiKey: String,
    systolic: Int,
    diastolic: Int,
    pulse: Int
): Result<String> = withContext(Dispatchers.IO) {
    try {
        val pulseText = if (pulse > 0) "with a pulse of $pulse bpm" else "(pulse not recorded)"
        val userMessage = "My blood pressure reading is $systolic/$diastolic mmHg $pulseText. What does this mean?"

        val body = JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", MAX_TOKENS)
            put("system", SYSTEM_PROMPT)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            }))
        }.toString()

        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 60_000
        }

        connection.outputStream.use { it.write(body.toByteArray()) }

        val responseCode = connection.responseCode
        if (responseCode != 200) {
            val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: ""
            val message = when (responseCode) {
                401 -> "Invalid API key. Check your key in settings."
                429 -> "Rate limited. Please try again in a moment."
                else -> "API error $responseCode: $errorBody"
            }
            return@withContext Result.failure(Exception(message))
        }

        val responseText = connection.inputStream.bufferedReader().readText()
        val json = JSONObject(responseText)
        val text = json.getJSONArray("content").getJSONObject(0).getString("text")
        Result.success(text)
    } catch (e: Exception) {
        Result.failure(Exception(e.message ?: "Unknown network error"))
    }
}

suspend fun getDailyAverageExplanation(
    apiKey: String,
    avgSystolic: Double,
    avgDiastolic: Double,
    avgPulse: Double?
): Result<String> = withContext(Dispatchers.IO) {
    try {
        val pulseText = if (avgPulse != null) "with an average pulse of ${"%.1f".format(avgPulse)} bpm" else "(pulse not recorded)"
        val userMessage = "My daily average blood pressure is ${"%.1f".format(avgSystolic)}/${"%.1f".format(avgDiastolic)} mmHg $pulseText. This is averaged across multiple readings taken on the same day. What does this mean?"

        val body = JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", MAX_TOKENS)
            put("system", DAILY_AVG_SYSTEM_PROMPT)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            }))
        }.toString()

        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 60_000
        }

        connection.outputStream.use { it.write(body.toByteArray()) }

        val responseCode = connection.responseCode
        if (responseCode != 200) {
            val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: ""
            val message = when (responseCode) {
                401 -> "Invalid API key. Check your key in settings."
                429 -> "Rate limited. Please try again in a moment."
                else -> "API error $responseCode: $errorBody"
            }
            return@withContext Result.failure(Exception(message))
        }

        val responseText = connection.inputStream.bufferedReader().readText()
        val json = JSONObject(responseText)
        val text = json.getJSONArray("content").getJSONObject(0).getString("text")
        Result.success(text)
    } catch (e: Exception) {
        Result.failure(Exception(e.message ?: "Unknown network error"))
    }
}
