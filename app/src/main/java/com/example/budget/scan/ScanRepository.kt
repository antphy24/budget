package com.example.budget.scan

import android.content.Context
import android.net.Uri
import com.example.budget.data.local.dao.PendingDao
import com.example.budget.data.local.entity.PendingEntity
import com.example.budget.domain.budget.Bootstrap
import com.example.budget.domain.model.Asset
import com.example.budget.domain.model.AssetType
import com.example.budget.domain.model.Transaction
import com.example.budget.domain.model.TransactionType
import com.example.budget.domain.repository.AssetRepository
import com.example.budget.domain.repository.ExpenseCategoryRepository
import com.example.budget.domain.repository.FundCategoryRepository
import com.example.budget.domain.repository.SettingsRepository
import com.example.budget.domain.repository.TransactionRepository
import com.example.budget.notifications.PocketAlertChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

enum class DraftKind { EXPENSE, INCOME, TRANSFER }

data class DraftItem(val name: String, val amount: Double, val category: String, val fundId: String?)

/** A transaction waiting in the review inbox. */
data class Draft(
    val id: String,
    val createdAt: Long,
    val source: String,
    val kind: DraftKind,
    val amount: Double,
    val timestamp: Long,
    val merchant: String,
    val category: String,
    val assetId: String?,
    val asset2Id: String?,
    val fundId: String?,
    val reference: String?,
    val items: List<DraftItem>,
    val splitItems: Boolean,
    val confidence: Double,
    val origin: String
)

data class ScanStatus(val working: Int = 0, val lastMessage: String? = null)

@Singleton
class ScanRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pendingDao: PendingDao,
    private val reader: TextReader,
    private val settings: SettingsRepository,
    private val assets: AssetRepository,
    private val funds: FundCategoryRepository,
    private val categories: ExpenseCategoryRepository,
    private val transactions: TransactionRepository,
    private val bootstrap: Bootstrap,
    private val alerts: PocketAlertChecker
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val insertLock = Mutex()
    private val _status = MutableStateFlow(ScanStatus())
    val status: StateFlow<ScanStatus> = _status.asStateFlow()

    val drafts: Flow<List<Draft>> = pendingDao.getAll().map { list -> list.map { it.toDraft() } }

    fun clearMessage() = _status.update { it.copy(lastMessage = null) }

    // ------------------------------------------------------------------ input

    /** Screenshots or photos: read on the phone, one by one, in the background. */
    fun scanImages(uris: List<Uri>, source: String) {
        if (uris.isEmpty()) return
        _status.update { it.copy(working = it.working + uris.size) }
        scope.launch {
            var added = 0
            var unreadable = 0
            for (uri in uris) {
                try {
                    val ctx = context()
                    val rows = reader.readRows(uri)
                    val parsed = PaymentParser.parseImage(rows, ctx.categoryNames)
                    added += addDrafts(parsed, source, if (source == "SHARE") "Shared screenshot" else "Scan", ctx)
                } catch (e: Exception) {
                    unreadable++
                } finally {
                    _status.update { it.copy(working = (it.working - 1).coerceAtLeast(0)) }
                }
            }
            _status.update {
                it.copy(lastMessage = when {
                    added == 0 && unreadable == uris.size -> "Couldn't open ${if (uris.size == 1) "that image" else "those images"}"
                    added == 0 -> "No payment found. Try a sharper photo with the total in view."
                    added == 1 -> "1 transaction ready to review"
                    else -> "$added transactions ready to review"
                })
            }
        }
    }

    /** A notification from a watched app. Quietly ignored when it isn't a payment. */
    fun readNotification(appName: String, text: String, postedAt: Long) {
        scope.launch {
            runCatching {
                val ctx = context()
                val parsed = PaymentParser.parseMessage(text, appName, ctx.categoryNames)
                    .map { p -> if (p.date == null) p.copy(date = day(postedAt), time = time(postedAt)) else p }
                addDrafts(parsed, "NOTIFICATION", "$appName notification", ctx)
            }
        }
    }

    // ------------------------------------------------------------------ matching

    private class Ctx(
        val assets: List<Asset>,
        val fundIds: Set<String>,
        val categoryNames: List<String>,
        val holdingId: String?,
        /** merchant (lowercase) → (category, pocket) from your confirmed history */
        val byMerchant: Map<String, Pair<String, String?>>,
        /** category → pocket you used with it last */
        val pocketForCategory: Map<String, String>,
        /** transaction type → account you used last */
        val lastAsset: Map<TransactionType, String>,
        val recent: List<Transaction>
    ) {
        val accountNames get() = assets.map { it.name }
    }

    private suspend fun context(): Ctx {
        bootstrap.ensure()
        val history = transactions.getAllTransactions().first()
        val assetList = assets.getAllAssets().first()
        return Ctx(
            assets = assetList,
            fundIds = funds.getAllFundCategories().first().map { it.id }.toSet(),
            categoryNames = categories.getAllExpenseCategories().first().map { it.name }.ifEmpty { listOf(Bootstrap.UNCATEGORIZED) },
            holdingId = settings.holdingFundId.value,
            byMerchant = history.asSequence()
                .filter { it.type == TransactionType.EXPENSE && it.memo.isNotBlank() }
                .distinctBy { it.memo.trim().lowercase() }
                .associate { it.memo.trim().lowercase() to (it.expenseCategory to it.primaryFundId) },
            pocketForCategory = history.asSequence()
                .filter { it.type == TransactionType.EXPENSE && it.primaryFundId != null }
                .distinctBy { it.expenseCategory }
                .associate { it.expenseCategory to it.primaryFundId!! },
            lastAsset = history.asSequence()
                .filter { it.primaryAssetId != null }
                .distinctBy { it.type }
                .associate { it.type to it.primaryAssetId!! },
            recent = history.filter { it.timestamp > System.currentTimeMillis() - 7L * 24 * 3600_000 }
        )
    }

    private val aliases = mapOf(
        "brimo" to "bri", "livin" to "mandiri", "gojek" to "gopay", "shopee" to "shopeepay",
        "tunai" to "cash", "dompet" to "cash", "bank syariah indonesia" to "bsi", "bank rakyat" to "bri",
        "bank central asia" to "bca", "bank negara" to "bni"
    )

    private fun matchAsset(hint: String?, assets: List<Asset>): String? {
        if (hint.isNullOrBlank()) return null
        var h = hint.lowercase().trim()
        aliases.forEach { (k, v) -> if (h.contains(k)) h = v }
        return assets.firstOrNull { it.name.equals(hint, true) }?.id
            ?: assets.firstOrNull { val n = it.name.lowercase(); n.contains(h) || h.contains(n) }?.id
    }

    private fun cashAsset(assets: List<Asset>) = assets.firstOrNull {
        val n = it.name.lowercase(); n.contains("cash") || n.contains("tunai") || n.contains("dompet")
    }?.id

    private fun matchCategory(name: String?, ctx: Ctx): String =
        ctx.categoryNames.firstOrNull { it.equals(name?.trim(), ignoreCase = true) } ?: Bootstrap.UNCATEGORIZED

    private fun toDraft(p: ParsedTx, source: String, origin: String, ctx: Ctx): Draft {
        val ts = parseTime(p.date, p.time) ?: System.currentTimeMillis()
        val fromHint = matchAsset(p.accountHint, ctx.assets)
        val liquid = ctx.assets.filter { it.type == AssetType.LIQUID }
        fun fallbackAsset(t: TransactionType) = ctx.lastAsset[t]?.takeIf { id -> ctx.assets.any { it.id == id } } ?: liquid.firstOrNull()?.id

        return when (p.kind) {
            "income" -> Draft(
                UUID.randomUUID().toString(), System.currentTimeMillis(), source, DraftKind.INCOME, p.amount, ts,
                p.merchant.ifBlank { "Income" }, Bootstrap.UNCATEGORIZED,
                fromHint ?: fallbackAsset(TransactionType.INCOME), null, ctx.holdingId, p.reference,
                emptyList(), false, p.confidence, origin
            )
            "atm_withdrawal", "transfer_out" -> {
                val to = if (p.kind == "atm_withdrawal") cashAsset(ctx.assets) else matchAsset(p.toAccountHint, ctx.assets)
                if (p.kind == "transfer_out" && to == null) {
                    // Money left to an account that isn't yours: it's spending
                    toDraft(p.copy(kind = "expense"), source, origin, ctx)
                } else Draft(
                    UUID.randomUUID().toString(), System.currentTimeMillis(), source, DraftKind.TRANSFER, p.amount, ts,
                    p.merchant.ifBlank { if (p.kind == "atm_withdrawal") "ATM withdrawal" else "Transfer" }, Bootstrap.UNCATEGORIZED,
                    fromHint ?: fallbackAsset(TransactionType.ASSET_TRANSFER), to, null, p.reference,
                    emptyList(), false, p.confidence, origin
                )
            }
            else -> {
                val learned = ctx.byMerchant[p.merchant.trim().lowercase()]
                val category = learned?.first ?: matchCategory(p.category, ctx)
                val pocket = learned?.second?.takeIf { it in ctx.fundIds } ?: ctx.pocketForCategory[category]?.takeIf { it in ctx.fundIds }
                val items = p.items.map { i ->
                    val c = matchCategory(i.category, ctx)
                    DraftItem(i.name, i.amount, c, ctx.pocketForCategory[c]?.takeIf { it in ctx.fundIds } ?: pocket)
                }
                Draft(
                    UUID.randomUUID().toString(), System.currentTimeMillis(), source, DraftKind.EXPENSE, p.amount, ts,
                    p.merchant.ifBlank { "Payment" }, category,
                    fromHint ?: fallbackAsset(TransactionType.EXPENSE), null, pocket, p.reference,
                    items, false, p.confidence, origin
                )
            }
        }
    }

    /** Adds drafts, skipping ones that duplicate a draft or a recent transaction. */
    private suspend fun addDrafts(parsed: List<ParsedTx>, source: String, origin: String, ctx: Ctx): Int = insertLock.withLock {
        val existing = pendingDao.getAllOnce().map { it.toDraft() }
        var added = 0
        for (p in parsed) {
            val d = toDraft(p, source, origin, ctx)
            val window = 20 * 60 * 1000L
            val dupDraft = existing.any { e ->
                (d.reference != null && e.reference == d.reference) ||
                    (abs(e.amount - d.amount) < 1 && e.kind == d.kind && abs(e.timestamp - d.timestamp) < window)
            }
            val dupTx = ctx.recent.any { t ->
                abs(t.totalAmount - d.amount) < 1 && abs(t.timestamp - d.timestamp) < window && t.type == d.kind.txType()
            }
            if (!dupDraft && !dupTx) {
                pendingDao.upsert(d.toEntity())
                added++
            }
        }
        added
    }

    // ------------------------------------------------------------------ review

    suspend fun save(draft: Draft) = pendingDao.upsert(draft.toEntity())
    suspend fun discard(id: String) = pendingDao.delete(id)

    /** Plain-language reason a draft can't be added yet, or null. */
    fun problem(d: Draft, assetList: List<Asset>): String? = when {
        d.amount <= 0 -> "Amount missing"
        d.assetId == null -> if (d.kind == DraftKind.INCOME) "Choose the account it went into" else "Choose the account"
        d.kind == DraftKind.TRANSFER && (d.asset2Id == null || d.asset2Id == d.assetId) -> "Choose where the money went"
        d.kind == DraftKind.EXPENSE && assetList.firstOrNull { it.id == d.assetId }?.type == AssetType.LIQUID &&
            (if (d.splitItems && d.items.isNotEmpty()) d.items.any { (it.fundId ?: d.fundId) == null } else d.fundId == null) ->
            "Choose a pocket"
        else -> null
    }

    /** Turns a draft into real transactions (one per pocket/category when split by item). */
    suspend fun confirm(d: Draft) = withContext(Dispatchers.IO) {
        val txns = toTransactions(d)
        transactions.processTransactions(txns)
        pendingDao.delete(d.id)
        txns.filter { it.type == TransactionType.EXPENSE }.mapNotNull { it.primaryFundId }.distinct()
            .forEach { alerts.onExpenseSaved(it) }
    }

    private fun toTransactions(d: Draft): List<Transaction> {
        fun tx(type: TransactionType, amount: Double, fund: String?, category: String, memo: String, asset2: String? = null) = Transaction(
            id = UUID.randomUUID().toString(), type = type, timestamp = d.timestamp, totalAmount = amount,
            primaryAssetId = d.assetId, secondaryAssetId = asset2, primaryFundId = fund, secondaryFundId = null,
            expenseCategory = category, memo = memo
        )
        return when (d.kind) {
            DraftKind.INCOME -> listOf(tx(TransactionType.INCOME, d.amount, d.fundId, Bootstrap.UNCATEGORIZED, d.merchant))
            DraftKind.TRANSFER -> listOf(tx(TransactionType.ASSET_TRANSFER, d.amount, null, Bootstrap.UNCATEGORIZED, d.merchant, d.asset2Id))
            DraftKind.EXPENSE -> {
                if (!d.splitItems || d.items.isEmpty()) {
                    listOf(tx(TransactionType.EXPENSE, d.amount, d.fundId, d.category, d.merchant))
                } else {
                    // Group lines by pocket + category; tax, fees or discounts go to the largest group
                    val groups = d.items.groupBy { (it.fundId ?: d.fundId) to it.category }
                        .map { (key, lines) -> Triple(key, lines.sumOf { it.amount }, lines.map { it.name }) }
                        .sortedByDescending { it.second }
                        .toMutableList()
                    val diff = d.amount - groups.sumOf { it.second }
                    if (groups.isNotEmpty() && abs(diff) >= 1) {
                        val first = groups[0]
                        groups[0] = Triple(first.first, max(0.0, first.second + diff), first.third)
                    }
                    groups.filter { it.second > 0 }.map { (key, amount, names) ->
                        tx(TransactionType.EXPENSE, amount, key.first, key.second, "${d.merchant}: ${names.joinToString(", ")}")
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun parseTime(date: String?, time: String?): Long? {
        if (date == null) return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse("$date ${time ?: "12:00"}")!!.time
        }.getOrNull()?.takeIf { it <= System.currentTimeMillis() + 24 * 3600_000L }
    }

    private fun DraftKind.txType() = when (this) {
        DraftKind.EXPENSE -> TransactionType.EXPENSE
        DraftKind.INCOME -> TransactionType.INCOME
        DraftKind.TRANSFER -> TransactionType.ASSET_TRANSFER
    }

    companion object {
        private fun day(t: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(t)
        private fun time(t: Long) = SimpleDateFormat("HH:mm", Locale.US).format(t)

        fun PendingEntity.toDraft() = Draft(
            id, createdAt, source, runCatching { DraftKind.valueOf(kind) }.getOrDefault(DraftKind.EXPENSE),
            amount, timestamp, merchant, category, assetId, asset2Id, fundId, reference,
            runCatching {
                val arr = JSONArray(itemsJson)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    DraftItem(o.optString("name"), o.optDouble("amount"), o.optString("category"),
                        if (o.isNull("fundId")) null else o.optString("fundId"))
                }
            }.getOrDefault(emptyList()),
            splitItems, confidence, origin
        )

        fun Draft.toEntity() = PendingEntity(
            id, createdAt, source, kind.name, amount, timestamp, merchant, category, assetId, asset2Id, fundId, reference,
            JSONArray().apply {
                items.forEach { put(JSONObject().put("name", it.name).put("amount", it.amount).put("category", it.category).put("fundId", it.fundId ?: JSONObject.NULL)) }
            }.toString(),
            splitItems, confidence, origin
        )
    }
}
