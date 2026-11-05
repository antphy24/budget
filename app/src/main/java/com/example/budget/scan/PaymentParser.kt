package com.example.budget.scan

/**
 * Turns the text of an Indonesian payment notification, a payment/transfer screenshot or a
 * store receipt (struk) into transactions, using rules only. Nothing leaves the phone.
 *
 * Pure Kotlin (no Android types) so it is covered by unit tests in PaymentParserTest.
 */
object PaymentParser {

    // ------------------------------------------------------------------ amounts

    /** "Rp 25.000", "Rp25.000,00", "IDR 1,250,000.00", "Rp. 5.500,-" */
    private val MONEY = Regex("""(?i)(?:rp\.?|idr)\s*([0-9][0-9.,]*[0-9])""")

    /** A plain price at the end of a receipt row: "9.900", "125,000", "15000" */
    private val TRAILING_NUMBER = Regex("""([0-9]{1,3}(?:[.,][0-9]{3})+|[0-9]{4,})(?:[.,][0-9]{2})?\s*-?\s*$""")

    /** Parses an Indonesian/English formatted number into whole rupiah. */
    fun parseAmount(raw: String): Double? {
        var s = raw.trim().removeSuffix("-").removeSuffix(",").removeSuffix(".").trim()
        if (s.isEmpty() || !s[0].isDigit()) return null
        // Drop decimals: a final separator followed by 1–2 digits ("25.000,00", "1,250,000.00", "25000.5")
        val dec = Regex("""^(.*\d)[.,](\d{1,2})$""").find(s)
        if (dec != null) {
            val head = dec.groupValues[1]
            // Only treat as decimals when the head is itself a valid amount (avoids "1.25" = 1.25 thousand ambiguity)
            if (head.contains('.') || head.contains(',') || head.length >= 3) s = head
        }
        val digits = s.replace(".", "").replace(",", "")
        if (digits.isEmpty() || !digits.all { it.isDigit() } || digits.length > 13) return null
        val v = digits.toDouble()
        return if (v > 0) v else null
    }

    private data class MoneyHit(val amount: Double, val start: Int, val end: Int)

    private fun moneyHits(text: String): List<MoneyHit> =
        MONEY.findAll(text).mapNotNull { m -> parseAmount(m.groupValues[1])?.let { MoneyHit(it, m.range.first, m.range.last) } }.toList()

    // ------------------------------------------------------------------ vocabulary

    private val SKIP = Regex(
        """(?i)\b(promo|voucher|diskon hingga|cashback hingga|dapatkan|ayo|yuk|klaim|kode otp|otp|jangan berikan|""" +
            """gagal|failed|dibatalkan|batal|pending|menunggu pembayaran|segera bayar|jatuh tempo|tagihan anda sebesar|""" +
            """limit|pinjaman tersedia|penawaran|undian|hadiah)\b"""
    )
    private val ATM = Regex("""(?i)(tarik tunai|penarikan tunai|penarikan atm|tarik dana|atm withdrawal|cash withdrawal)""")
    private val TOPUP = Regex("""(?i)(top ?up|isi saldo|topup)""")
    private val INCOME = Regex(
        """(?i)(dana masuk|uang masuk|transfer masuk|saldo masuk|kamu menerima|anda menerima|menerima (dana|uang|transfer)|""" +
            """diterima dari|terima dana|terima uang|mendapatkan transfer|kiriman uang|incoming|received|refund|pengembalian dana|""" +
            """\bcr\b|\(cr\)|kredit\b|cashback (sebesar|rp))"""
    )
    private val EXPENSE = Regex(
        """(?i)(pembayaran|berhasil (dibayar|membayar|bayar)|bayar|membayar|qris|pembelian|transfer (ke|keluar|berhasil)|""" +
            """kirim (uang|dana)|mengirim|debit|\bdb\b|\(db\)|purchase|paid|payment|transaksi berhasil|belanja|""" +
            """pemotongan saldo|autodebet|\btransfer\b)"""
    )

    private val BANKS = listOf(
        "BRI" to Regex("""(?i)\b(bri|brimo|bank rakyat)\b"""),
        "BCA" to Regex("""(?i)\b(bca|klikbca|mybca|m-bca)\b"""),
        "Mandiri" to Regex("""(?i)\b(mandiri|livin)"""),
        "BNI" to Regex("""(?i)\b(bni|wondr)\b"""),
        "BSI" to Regex("""(?i)\b(bsi|bank syariah indonesia)\b"""),
        "BTN" to Regex("""(?i)\bbtn\b"""),
        "CIMB" to Regex("""(?i)\bcimb\b"""),
        "Jago" to Regex("""(?i)\bjago\b"""),
        "SeaBank" to Regex("""(?i)\bseabank\b"""),
        "DANA" to Regex("""(?i)\bdana\b(?! masuk)"""),
        "GoPay" to Regex("""(?i)\b(gopay|gojek)\b"""),
        "OVO" to Regex("""(?i)\bovo\b"""),
        "ShopeePay" to Regex("""(?i)\b(shopeepay|spaylater)\b"""),
        "LinkAja" to Regex("""(?i)\blinkaja\b""")
    )

    private val REF = Regex(
        """(?i)(?:no\.?\s*ref(?:erensi)?|ref(?:erence)?(?:\s*no\.?)?|id\s*transaksi|no\.?\s*transaksi|kode\s*transaksi|trx\s*id|transaction\s*id)\s*[:.#]?\s*([A-Z0-9][A-Z0-9-]{5,})"""
    )

    // Stop words that end a merchant/payee name
    private const val NAME_STOP = """(?=\s+(?:sebesar|senilai|rp|idr|berhasil|pada|tanggal|tgl|melalui|via|dengan|telah|sudah|untuk|dari|ke)\b|[.,;:!\n(]|$)"""
    private val PAYEE = Regex("""(?i)(?:\bke|\bkepada|\bdi|merchant|penerima|tujuan|toko|nama merchant|payee)\s*:?\s+([A-Za-z0-9][A-Za-z0-9 &'./-]{1,40}?)$NAME_STOP""")
    private val PAYER = Regex("""(?i)(?:\bdari|pengirim|sender)\s*:?\s+([A-Za-z0-9][A-Za-z0-9 &'./-]{1,40}?)$NAME_STOP""")

    // ------------------------------------------------------------------ dates

    private val MONTHS = mapOf(
        "jan" to 1, "feb" to 2, "peb" to 2, "mar" to 3, "apr" to 4, "mei" to 5, "may" to 5, "jun" to 6, "jul" to 7,
        "agu" to 8, "agt" to 8, "ags" to 8, "aug" to 8, "sep" to 9, "okt" to 10, "oct" to 10, "nov" to 11, "nop" to 11,
        "des" to 12, "dec" to 12
    )
    private val DATE_NUM = Regex("""\b(\d{1,2})[/.-](\d{1,2})[/.-](\d{2,4})\b""")
    private val DATE_ISO = Regex("""\b(20\d{2})-(\d{1,2})-(\d{1,2})\b""")
    private val DATE_TEXT = Regex("""(?i)\b(\d{1,2})\s+([a-z]{3,9})\.?\s+(\d{4})\b""")
    private val TIME = Regex("""\b([01]?\d|2[0-3])[:.]([0-5]\d)(?:[:.][0-5]\d)?\s*(?:wib|wita|wit)?\b""", RegexOption.IGNORE_CASE)

    fun findDate(text: String): String? {
        DATE_ISO.find(text)?.let { m ->
            return fmt(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        DATE_TEXT.find(text)?.let { m ->
            val mon = MONTHS[m.groupValues[2].lowercase().take(3)]
            if (mon != null) return fmt(m.groupValues[3].toInt(), mon, m.groupValues[1].toInt())
        }
        DATE_NUM.find(text)?.let { m ->
            var y = m.groupValues[3].toInt()
            if (y < 100) y += 2000
            return fmt(y, m.groupValues[2].toInt(), m.groupValues[1].toInt())
        }
        return null
    }

    fun findTime(text: String): String? {
        // Skip "times" that are really parts of dates ("05.11.2026") or amounts ("25.000")
        return TIME.findAll(text).firstOrNull { m ->
            val b1 = text.getOrNull(m.range.first - 1)
            val b2 = text.getOrNull(m.range.first - 2)
            val a1 = text.getOrNull(m.range.last + 1)
            val a2 = text.getOrNull(m.range.last + 2)
            b1?.isDigit() != true && a1?.isDigit() != true &&
                !(a1 != null && a1 in "./-" && a2?.isDigit() == true) &&
                !(b1 != null && b1 in "./-" && b2?.isDigit() == true)
        }?.let { "%02d:%s".format(it.groupValues[1].toInt(), it.groupValues[2]) }
    }

    private fun fmt(y: Int, m: Int, d: Int): String? =
        if (y in 2000..2100 && m in 1..12 && d in 1..31) "%04d-%02d-%02d".format(y, m, d) else null

    // ------------------------------------------------------------------ helpers

    private fun clean(name: String): String {
        val n = name.trim().trim('-', '.', ':', '*').replace(Regex("\\s+"), " ")
        if (n.isEmpty()) return n
        // Title-case ALL CAPS names; keep short brand caps like "BRI", "PLN"
        return if (n == n.uppercase() && n.length > 4) {
            n.lowercase().split(' ').joinToString(" ") { w -> if (w.length <= 3 && w.all { it.isLetter() } && w in setOf("pt", "cv", "rm", "tb", "ud")) w.uppercase() else w.replaceFirstChar { it.uppercase() } }
        } else n
    }

    private fun bankIn(text: String): String? = BANKS.firstOrNull { it.second.containsMatchIn(text) }?.first

    private fun normalizeAppName(app: String?): String? = app?.let { a -> bankIn(a) ?: a }

    /** Picks the payment amount, preferring ones next to "sebesar/nominal/total" and avoiding balances. */
    private fun pickAmount(text: String, hits: List<MoneyHit>): Double? {
        if (hits.isEmpty()) return null
        val scored = hits.map { h ->
            val before = text.substring(maxOf(0, h.start - 28), h.start).lowercase()
            var score = 0
            if (Regex("""(sebesar|senilai|nominal|jumlah|total|amount|bayar|pembayaran|transfer|tarik|masuk|menerima)\s*:?\s*$""").containsMatchIn(before)) score += 3
            if (Regex("""(saldo|balance|sisa|limit|biaya admin|admin|fee|cashback|poin|point|diskon|potongan)\s*(akhir|anda|kamu|tersedia)?\s*:?\s*$""").containsMatchIn(before)) score -= 5
            h to score
        }
        return scored.maxByOrNull { it.second * 10 - hits.indexOf(it.first) }?.first?.amount
    }

    // ------------------------------------------------------------------ notifications & payment screenshots

    /**
     * A notification or a payment/transfer confirmation (short text). Returns at most one transaction;
     * empty when it is a promo, OTP, failed payment or has no amount.
     */
    fun parseMessage(text: String, appName: String? = null, categories: List<String> = emptyList()): List<ParsedTx> {
        val t = text.replace(' ', ' ')
        if (SKIP.containsMatchIn(t)) return emptyList()
        val hits = moneyHits(t)
        val amount = pickAmount(t, hits) ?: return emptyList()

        val kind = when {
            ATM.containsMatchIn(t) -> "atm_withdrawal"
            TOPUP.containsMatchIn(t) -> "transfer_out"
            INCOME.containsMatchIn(t) && !Regex("""(?i)(pembayaran|bayar|transfer ke|kirim)""").containsMatchIn(t) -> "income"
            EXPENSE.containsMatchIn(t) -> "expense"
            INCOME.containsMatchIn(t) -> "income"
            else -> return emptyList()
        }

        val appBank = normalizeAppName(appName)
        val textBank = bankIn(t)
        val name = when (kind) {
            "income" -> PAYER.find(t)?.groupValues?.get(1)
            "atm_withdrawal" -> null
            else -> PAYEE.find(t)?.groupValues?.get(1)?.takeUnless { BANKS.any { b -> b.second.matches(it.trim()) } }
        }?.let(::clean)

        val merchant = when {
            kind == "atm_withdrawal" -> "ATM withdrawal"
            kind == "transfer_out" -> "Top up ${appBank ?: textBank ?: ""}".trim()
            !name.isNullOrBlank() -> if (kind == "expense" && Regex("""(?i)transfer|kirim""").containsMatchIn(t) && !Regex("""(?i)qris|merchant|bayar""").containsMatchIn(t)) "Transfer to $name" else name
            kind == "income" -> "Income${appBank?.let { " ($it)" } ?: ""}"
            else -> "Payment${appBank?.let { " ($it)" } ?: ""}"
        }

        return listOf(
            ParsedTx(
                kind = kind,
                amount = amount,
                date = findDate(t),
                time = findTime(t),
                merchant = merchant,
                accountHint = when (kind) {
                    "transfer_out" -> textBank?.takeIf { it != appBank }
                    else -> appBank ?: textBank
                },
                toAccountHint = if (kind == "transfer_out") appBank ?: textBank else null,
                reference = REF.find(t)?.groupValues?.get(1),
                category = if (kind == "expense") CategoryGuesser.guess("$merchant $t", categories) else null,
                items = emptyList(),
                confidence = if (hits.size == 1 || name != null) 0.9 else 0.7
            )
        )
    }

    // ------------------------------------------------------------------ images

    /**
     * Text read from a screenshot or photo, as rows (top to bottom, each row's pieces joined
     * left to right). Payment confirmations go through [parseMessage]; everything else is
     * treated as a store receipt.
     */
    fun parseImage(rows: List<String>, categories: List<String> = emptyList()): List<ParsedTx> {
        val all = rows.joinToString("\n")
        val looksLikePayment = Regex(
            """(?i)(transaksi berhasil|pembayaran berhasil|transfer berhasil|bukti transfer|bukti pembayaran|berhasil dibayar|qris|""" +
                """top up berhasil|tarik tunai|dana masuk|penerimaan|receipt of transfer|payment successful)"""
        ).containsMatchIn(all)
        // Store-receipt words; plain "total" also appears on transfer screens, so it doesn't count
        val looksLikeReceipt = Regex("""(?i)\b(subtotal|tunai|kembali|kembalian|kasir|ppn|struk|harga jual|qty)\b""").containsMatchIn(all) &&
            rows.count { TRAILING_NUMBER.containsMatchIn(it) } >= 2

        if (looksLikePayment && !looksLikeReceipt) {
            val p = parseMessage(all, null, categories)
            if (p.isNotEmpty()) return p
        }
        return parseReceipt(rows, categories)?.let { listOf(it) } ?: parseMessage(all, null, categories)
    }

    private val TOTAL_LINE = Regex("""(?i)^\s*(grand\s*total|total\s*(belanja|bayar|pembayaran|harga|tagihan)?|jumlah\s*(bayar|total)?|total\s*rp)\b""")
    private val NOT_TOTAL = Regex("""(?i)(total\s*(item|qty|disc|diskon|hemat|potongan|point|poin)|sub\s*total)""")
    private val NOT_ITEM = Regex(
        """(?i)\b(total|subtotal|sub total|tunai|cash|kembali|kembalian|change|ppn|dpp|pajak|tax|diskon|disc|hemat|potongan|""" +
            """harga jual|npwp|telp|telepon|kasir|member|poin|point|saldo|debit|kredit|card|kartu|bayar|voucher|no\.?|tgl|tanggal|jam|struk|terima kasih)\b"""
    )
    private val CHAIN = Regex("""(?i)(indomaret|alfamart|alfamidi|superindo|hypermart|transmart|lotte|giant|carrefour|guardian|watsons|kimia farma|apotek k24|century|gramedia|pertamina|starbucks|kfc|mcdonald|mixue)""")

    /** A store receipt (struk): merchant from the header, TOTAL as the amount, item lines as items. */
    fun parseReceipt(rows: List<String>, categories: List<String> = emptyList()): ParsedTx? {
        if (rows.isEmpty()) return null
        val all = rows.joinToString("\n")

        // Amount: the TOTAL line; otherwise the largest price above "TUNAI"/"KEMBALI"
        var totalIndex = rows.indexOfFirst { TOTAL_LINE.containsMatchIn(it) && !NOT_TOTAL.containsMatchIn(it) && TRAILING_NUMBER.containsMatchIn(it) }
        var amount = totalIndex.takeIf { it >= 0 }?.let { lastNumber(rows[it]) }
        if (amount == null) {
            // "TOTAL" and its number sometimes land on separate rows
            val idx = rows.indexOfFirst { TOTAL_LINE.containsMatchIn(it) && !NOT_TOTAL.containsMatchIn(it) }
            if (idx >= 0) {
                amount = rows.drop(idx + 1).take(2).firstNotNullOfOrNull { lastNumber(it) }
                totalIndex = idx
            }
        }
        val confident = amount != null
        if (amount == null) {
            val stop = rows.indexOfFirst { Regex("""(?i)\b(tunai|cash|kembali|kembalian)\b""").containsMatchIn(it) }.let { if (it < 0) rows.size else it }
            amount = rows.take(stop).mapNotNull { lastNumber(it) }.maxOrNull()
                ?: moneyHits(all).maxOfOrNull { it.amount }
        }
        amount ?: return null

        // Merchant: a known chain anywhere, else the first wordy line of the header
        val merchant = CHAIN.find(all)?.value?.let(::clean)
            ?: rows.take(4).firstOrNull { r ->
                r.count { it.isLetter() } >= 3 && r.count { it.isDigit() } <= 2 && !NOT_ITEM.containsMatchIn(r)
            }?.let(::clean)
            ?: "Receipt"

        // Items: rows above the total that end in a price
        val itemRows = (if (totalIndex > 0) rows.take(totalIndex) else rows).drop(1)
        val items = itemRows.mapNotNull { r ->
            if (NOT_ITEM.containsMatchIn(r)) return@mapNotNull null
            val price = lastNumber(r) ?: return@mapNotNull null
            val name = r.replace(TRAILING_NUMBER, "")
                .replace(Regex("""\s+\d+\s*[xX@]?\s*[0-9.,]*\s*$"""), "") // qty and unit price columns
                .trim().trim('-', ':', '.', '*')
            if (name.count { it.isLetter() } < 2) null
            else ParsedItem(clean(name), price, CategoryGuesser.guess(name, categories))
        }.filter { it.amount <= amount }
        val itemsOk = items.size >= 2 && items.sumOf { it.amount } <= amount * 1.25

        return ParsedTx(
            kind = "expense",
            amount = amount,
            date = findDate(all),
            time = findTime(all),
            merchant = merchant,
            accountHint = bankIn(all.lines().filter { Regex("""(?i)debit|kartu|card|qris|e-?money|flazz""").containsMatchIn(it) }.joinToString(" ")),
            toAccountHint = null,
            reference = REF.find(all)?.groupValues?.get(1),
            category = CategoryGuesser.guess("$merchant $all", categories),
            items = if (itemsOk) items else emptyList(),
            confidence = if (confident) 0.85 else 0.5
        )
    }

    private fun lastNumber(row: String): Double? =
        TRAILING_NUMBER.find(row.trim())?.groupValues?.get(1)?.let(::parseAmount)?.takeIf { it >= 100 }
}
