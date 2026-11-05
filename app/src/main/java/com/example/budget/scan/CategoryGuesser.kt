package com.example.budget.scan

/**
 * Guesses a spending category from a merchant name, item name or receipt text using
 * Indonesian keywords. Runs entirely on the phone. Your own history (what you chose for a
 * merchant before) always wins over these guesses; see ScanRepository.
 */
object CategoryGuesser {

    /** Canonical category → keywords. Canonical names match the app's starter categories. */
    private val rules: List<Pair<String, List<String>>> = listOf(
        "Groceries" to listOf(
            "indomaret", "alfamart", "alfamidi", "superindo", "hypermart", "transmart", "lotte mart", "hero ",
            "giant", "carrefour", "toserba", "swalayan", "minimarket", "pasar", "sembako", "grosir",
            "beras", "telur", "minyak goreng", "gula", "tepung", "sayur", "daging", "ayam potong", "ikan", "susu",
            "sabun", "shampo", "sampo", "pasta gigi", "odol", "deterjen", "rinso", "tisu", "tissue", "popok", "pampers"
        ),
        "Food & drinks" to listOf(
            "gofood", "grabfood", "shopeefood", "warung", "warteg", "rumah makan", "rm ", "resto", "restaurant",
            "cafe", "kafe", "coffee", "kopi", "kenangan", "janji jiwa", "starbucks", "mcd", "mcdonald", "kfc",
            "burger", "pizza", "bakso", "mie ", "mi ayam", "nasi", "sate", "soto", "ayam geprek", "martabak",
            "chatime", "mixue", "es teh", "boba", "bakery", "roti", "snack", "chitato", "coklat", "biskuit",
            "minuman", "aqua", "teh botol", "jajan", "makan", "food", "kuliner", "katering", "catering"
        ),
        "Transport" to listOf(
            "pertamina", "spbu", "shell", "bp ", "vivo", "pertalite", "pertamax", "solar", "bensin", "bbm",
            "goride", "gocar", "grabbike", "grabcar", "gojek", "grab", "maxim", "indriver", "ojek", "taxi", "taksi",
            "parkir", "parking", "tol", "e-toll", "etoll", "flazz", "brizzi", "kai ", "kereta", "krl", "mrt",
            "transjakarta", "bus", "travel", "pelni", "kapal", "garuda", "lion air", "citilink", "batik air",
            "traveloka", "tiket.com", "bengkel", "servis motor", "oli"
        ),
        "Phone & internet" to listOf(
            "telkomsel", "indosat", "im3", "xl ", "axis", "tri ", "smartfren", "by.u", "pulsa", "kuota", "paket data",
            "indihome", "biznet", "first media", "myrepublic", "wifi", "internet"
        ),
        "Bills" to listOf(
            "pln", "token listrik", "listrik", "pdam", "air minum", "bpjs", "pajak", "pbb", "samsat", "iuran",
            "tagihan", "angsuran", "cicilan", "kredit", "asuransi", "kos", "kost", "sewa", "kontrakan"
        ),
        "Health" to listOf(
            "apotek", "apotik", "kimia farma", "guardian", "century", "k24", "klinik", "rumah sakit", "rs ",
            "puskesmas", "dokter", "dr.", "obat", "vitamin", "halodoc", "alodokter", "lab "
        ),
        "Education" to listOf(
            "sekolah", "spp", "kampus", "universitas", "kuliah", "kursus", "les ", "buku", "gramedia", "toko buku",
            "fotokopi", "fotocopy", "print", "atk", "alat tulis", "udemy", "coursera", "ruangguru"
        ),
        "Shopping" to listOf(
            "shopee", "tokopedia", "lazada", "blibli", "bukalapak", "tiktok shop", "zalora", "uniqlo", "matahari",
            "ramayana", "erafone", "ibox", "electronic", "elektronik", "baju", "sepatu", "fashion"
        ),
        "Giving" to listOf(
            "masjid", "mushola", "musholla", "infaq", "infak", "sedekah", "shodaqoh", "zakat", "baznas", "lazis",
            "dompet dhuafa", "rumah zakat", "kitabisa", "donasi", "wakaf", "panti"
        ),
        "Entertainment" to listOf(
            "netflix", "spotify", "youtube", "disney", "vidio", "viu", "steam", "playstation", "google play",
            "app store", "bioskop", "cgv", "xxi", "cinepolis", "game", "top up game", "mobile legends", "diamond"
        ),
        "Family" to listOf("keluarga", "orang tua", "ibu ", "bapak", "kiriman", "uang saku")
    )

    /**
     * Returns the best matching category from [available] (the user's own list), or null.
     * Canonical names are matched to the user's categories case-insensitively, also by
     * the first word ("Food" matches "Food & drinks").
     */
    fun guess(text: String, available: List<String>): String? {
        val t = " " + text.lowercase().replace(Regex("[^a-z0-9.&+ ]"), " ").replace(Regex("\\s+"), " ") + " "
        var best: String? = null
        var bestScore = 0
        for ((canonical, keywords) in rules) {
            val score = keywords.sumOf { k -> if (t.contains(k)) k.trim().length else 0 }
            if (score > bestScore) {
                best = canonical; bestScore = score
            }
        }
        return best?.let { mapToUser(it, available) }
    }

    fun mapToUser(canonical: String, available: List<String>): String? =
        available.firstOrNull { it.equals(canonical, ignoreCase = true) }
            ?: available.firstOrNull { it.substringBefore(" ").equals(canonical.substringBefore(" "), ignoreCase = true) }
}
