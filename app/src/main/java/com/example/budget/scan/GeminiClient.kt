package com.example.budget.scan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** One money movement read from an image or notification. */
data class ParsedTx(
    /** expense | income | transfer_out | atm_withdrawal */
    val kind: String,
    val amount: Double,
    /** yyyy-MM-dd or null */
    val date: String?,
    /** HH:mm or null */
    val time: String?,
    val merchant: String,
    val accountHint: String?,
    val toAccountHint: String?,
    val reference: String?,
    val category: String?,
    val items: List<ParsedItem>,
    val confidence: Double
)

data class ParsedItem(val name: String, val amount: Double, val category: String?)

class GeminiException(message: String, val userMessage: String) : IOException(message)

/**
 * Reads receipts, payment screenshots and notification text with Gemini (generateContent REST).
 * Tries newer models first and falls back when one isn't available to the key.
 */
@Singleton
class GeminiClient @Inject constructor() {

    private val models = listOf("gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-2.5-flash")
    @Volatile private var workingModel: String? = null

    suspend fun readImage(apiKey: String, jpegBase64: String, categories: List<String>, accounts: List<String>): List<ParsedTx> =
        call(apiKey, listOf(
            JSONObject().put("text", prompt(categories, accounts, source = "image")),
            JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", jpegBase64))
        ))

    suspend fun readText(apiKey: String, appName: String, text: String, categories: List<String>, accounts: List<String>): List<ParsedTx> =
        call(apiKey, listOf(
            JSONObject().put("text", prompt(categories, accounts, source = "notification") +
                "\n\nNotification from the app \"$appName\":\n" + text)
        ))

    private suspend fun call(apiKey: String, parts: List<JSONObject>): List<ParsedTx> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw GeminiException("no key", "Add your Gemini API key in Settings first.")
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray(parts))))
            .put("generationConfig", JSONObject()
                .put("responseMimeType", "application/json")
                .put("temperature", 0))
            .toString()

        val order = listOfNotNull(workingModel) + models.filter { it != workingModel }
        var last: Exception? = null
        for (model in order) {
            try {
                val text = post(model, apiKey, body)
                workingModel = model
                return@withContext parse(text)
            } catch (e: ModelUnavailable) {
                last = e
                continue
            }
        }
        throw GeminiException("no model: ${last?.message}", "Gemini didn't accept the request with any model. Check that your key is active.")
    }

    private class ModelUnavailable(msg: String) : IOException(msg)

    private fun post(model: String, apiKey: String, body: String): String {
        val conn = (URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            when {
                code in 200..299 -> {
                    val json = JSONObject(resp)
                    val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
                    val partsOut = candidate?.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
                    val sb = StringBuilder()
                    for (i in 0 until partsOut.length()) {
                        val p = partsOut.getJSONObject(i)
                        if (!p.optBoolean("thought", false)) sb.append(p.optString("text"))
                    }
                    return sb.toString()
                }
                code == 404 -> throw ModelUnavailable("$model: 404")
                code == 400 && resp.contains("model", ignoreCase = true) && !resp.contains("API key", ignoreCase = true) ->
                    throw ModelUnavailable("$model: 400")
                code == 400 || code == 401 || code == 403 ->
                    throw GeminiException("auth $code: $resp", "Gemini rejected the API key. Check it in Settings.")
                code == 429 ->
                    throw GeminiException("quota", "Gemini's free limit is used up for now. Try again in a minute.")
                else -> throw GeminiException("http $code: $resp", "Gemini is having trouble right now (error $code). Try again later.")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(text: String): List<ParsedTx> {
        val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        if (cleaned.isEmpty()) return emptyList()
        val root = if (cleaned.startsWith("[")) JSONObject().put("transactions", JSONArray(cleaned)) else JSONObject(cleaned)
        val arr = root.optJSONArray("transactions") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val amount = o.optDouble("amount", 0.0)
            if (amount.isNaN() || amount <= 0) return@mapNotNull null
            val itemsArr = o.optJSONArray("items") ?: JSONArray()
            ParsedTx(
                kind = o.optString("kind", "expense").lowercase(),
                amount = amount,
                date = o.optStringOrNull("date"),
                time = o.optStringOrNull("time"),
                merchant = o.optString("merchant", "").trim(),
                accountHint = o.optStringOrNull("account_hint"),
                toAccountHint = o.optStringOrNull("to_account_hint"),
                reference = o.optStringOrNull("reference"),
                category = o.optStringOrNull("category"),
                items = (0 until itemsArr.length()).mapNotNull { j ->
                    val it = itemsArr.optJSONObject(j) ?: return@mapNotNull null
                    val a = it.optDouble("amount", 0.0)
                    if (a.isNaN() || a <= 0) null else ParsedItem(it.optString("name").trim(), a, it.optStringOrNull("category"))
                },
                confidence = o.optDouble("confidence", 0.7).let { if (it.isNaN()) 0.7 else it }
            )
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key, "").trim().takeIf { it.isNotEmpty() && it != "null" }

    private fun prompt(categories: List<String>, accounts: List<String>, source: String) = """
You read Indonesian personal-finance documents for the owner of this phone: store receipts (struk), QRIS payment confirmations, bank transfer receipts, e-wallet screenshots (DANA, GoPay, OVO, ShopeePay), mobile-banking screens (BRImo, BCA, Livin', BSI, BNI), ATM slips, bank mutation lists, and payment notifications.

Extract every completed money movement of the owner in this $source. Ignore promotions, ads, OTP codes, balance-only messages, failed or pending payments, and bills that were not paid yet.

Return only JSON: {"transactions": [ ... ]}. Each transaction:
- "kind": "expense" (owner paid someone, incl. transfers to other people and QRIS), "income" (owner received money), "transfer_out" (owner moved money to their own other account or topped up their own e-wallet), or "atm_withdrawal" (cash taken from an ATM)
- "amount": number in rupiah, the total actually paid or received including tax/admin fee. Indonesian format: "Rp 1.250.000" = 1250000, "Rp25.000,00" = 25000.
- "date": "YYYY-MM-DD" or null; "time": "HH:MM" (24h) or null
- "merchant": short, clean payee/merchant/sender name in Title Case (e.g. "Indomaret", "Warung Bu Sri", "Transfer to Andi"); for income the sender or source
- "account_hint": the owner's account the money left or entered (bank or e-wallet name), or null
- "to_account_hint": for transfer_out, the owner's receiving account; else null
- "reference": transaction/reference number if shown, else null
- "category": exactly one of: ${categories.joinToString(", ") { "\"$it\"" }}
- "items": for itemized store receipts, each line as {"name", "amount", "category"} (category from the same list); otherwise []
- "confidence": 0 to 1, how sure you are about the amount and kind

The owner's accounts are: ${if (accounts.isEmpty()) "unknown" else accounts.joinToString(", ")}. Use these names for account hints when they match.
If there is nothing financial, return {"transactions": []}.
""".trimIndent()
}
