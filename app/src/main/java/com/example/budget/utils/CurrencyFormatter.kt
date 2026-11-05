package com.example.budget.utils

import java.text.NumberFormat
import java.util.Locale

object CurrencyFormatter {
    fun formatToIDR(amount: Double, hideBalance: Boolean = false): String {
        if (hideBalance) return "Rp ***.***"
        val localeID = Locale("id", "ID")
        val format = NumberFormat.getCurrencyInstance(localeID)
        return format.format(amount).replace("Rp", "Rp ").replace(",00", "")
    }
}
