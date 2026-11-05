package com.example.budget.scan

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
