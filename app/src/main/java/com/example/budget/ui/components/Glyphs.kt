package com.example.budget.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/** Picks an icon from a category or pocket name (English or Indonesian keywords). */
fun iconForName(name: String): ImageVector {
    val n = name.lowercase()
    fun has(vararg keys: String) = keys.any { n.contains(it) }
    return when {
        has("grocer", "belanja dapur", "sembako", "market", "pasar") -> Icons.Outlined.LocalGroceryStore
        has("food", "makan", "dining", "drink", "minum", "kopi", "coffee", "jajan", "snack") -> Icons.Outlined.Restaurant
        has("transport", "bensin", "fuel", "ojek", "grab", "gojek", "motor", "parkir", "travel") -> Icons.Outlined.TwoWheeler
        has("bill", "tagihan", "utilit", "listrik", "pln", "air", "wifi", "internet") -> Icons.Outlined.ReceiptLong
        has("pulsa", "phone", "data", "kuota") -> Icons.Outlined.PhoneAndroid
        has("kos", "rent", "sewa", "rumah", "home", "house") -> Icons.Outlined.Home
        has("shop", "fashion", "baju", "cloth") -> Icons.Outlined.ShoppingBag
        has("health", "sehat", "obat", "doctor", "dokter", "medic") -> Icons.Outlined.HealthAndSafety
        has("educat", "school", "sekolah", "kuliah", "book", "buku", "course") -> Icons.Outlined.School
        has("family", "keluarga", "orang tua", "parents", "kids", "anak") -> Icons.Outlined.FamilyRestroom
        has("sedekah", "zakat", "infaq", "infak", "giving", "charity", "donat") -> Icons.Outlined.VolunteerActivism
        has("gift", "hadiah", "kado") -> Icons.Outlined.CardGiftcard
        has("wedding", "nikah", "pernikahan") -> Icons.Outlined.Favorite
        has("entertain", "hiburan", "movie", "film", "game", "langganan", "subscri") -> Icons.Outlined.Movie
        has("saving", "tabung", "emergency", "darurat", "invest") -> Icons.Outlined.Savings
        has("salary", "gaji", "income", "honor", "bonus") -> Icons.Outlined.Payments
        has("distribute", "unallocated") -> Icons.Outlined.Inbox
        else -> Icons.Outlined.Category
    }
}
