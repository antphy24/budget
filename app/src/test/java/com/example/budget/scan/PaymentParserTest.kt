package com.example.budget.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentParserTest {

    private val cats = listOf(
        "Food & drinks", "Groceries", "Transport", "Bills", "Phone & internet",
        "Shopping", "Health", "Education", "Family", "Giving", "Entertainment", "Uncategorized"
    )

    private fun one(text: String, app: String? = null): ParsedTx {
        val r = PaymentParser.parseMessage(text, app, cats)
        assertEquals("expected one transaction from: $text", 1, r.size)
        return r[0]
    }

    // ---- amounts

    @Test fun amounts() {
        assertEquals(25000.0, PaymentParser.parseAmount("25.000")!!, 0.0)
        assertEquals(25000.0, PaymentParser.parseAmount("25.000,00")!!, 0.0)
        assertEquals(1250000.0, PaymentParser.parseAmount("1.250.000")!!, 0.0)
        assertEquals(1250000.0, PaymentParser.parseAmount("1,250,000.00")!!, 0.0)
        assertEquals(5500.0, PaymentParser.parseAmount("5.500,-")!!, 0.0)
        assertEquals(15000.0, PaymentParser.parseAmount("15000")!!, 0.0)
        assertNull(PaymentParser.parseAmount("abc"))
    }

    // ---- notifications

    @Test fun danaQris() {
        val t = one("Pembayaran Berhasil\nKamu berhasil bayar Rp25.000 ke Warung Bu Sri", "DANA")
        assertEquals("expense", t.kind)
        assertEquals(25000.0, t.amount, 0.0)
        assertEquals("Warung Bu Sri", t.merchant)
        assertEquals("DANA", t.accountHint)
        assertEquals("Food & drinks", t.category)
    }

    @Test fun gopayMerchant() {
        val t = one("Pembayaran ke KOPI KENANGAN sebesar Rp32.000 berhasil", "Gojek")
        assertEquals("expense", t.kind)
        assertEquals(32000.0, t.amount, 0.0)
        assertEquals("Kopi Kenangan", t.merchant)
        assertEquals("GoPay", t.accountHint)
        assertEquals("Food & drinks", t.category)
    }

    @Test fun brimoTransferOut() {
        val t = one("Transaksi Transfer sebesar Rp 150.000,00 ke ANDI SAPUTRA berhasil pada 05/11/2026 10:21:33", "BRImo")
        assertEquals("expense", t.kind)
        assertEquals(150000.0, t.amount, 0.0)
        assertEquals("Transfer to Andi Saputra", t.merchant)
        assertEquals("BRI", t.accountHint)
        assertEquals("2026-11-05", t.date)
        assertEquals("10:21", t.time)
    }

    @Test fun incomeWithBalanceIgnored() {
        val t = one("Dana masuk Rp 1.250.000 dari PT MAJU JAYA. Saldo akhir Rp 3.400.000", "BCA mobile")
        assertEquals("income", t.kind)
        assertEquals(1250000.0, t.amount, 0.0)
        assertEquals("PT Maju Jaya", t.merchant)
        assertEquals("BCA", t.accountHint)
    }

    @Test fun atmWithdrawal() {
        val t = one("Tarik Tunai Rp500.000 di ATM BRI berhasil. Ref: 823645119087", "BRImo")
        assertEquals("atm_withdrawal", t.kind)
        assertEquals(500000.0, t.amount, 0.0)
        assertEquals("823645119087", t.reference)
    }

    @Test fun topUp() {
        val t = one("Top Up Rp100.000 berhasil dari BCA", "DANA")
        assertEquals("transfer_out", t.kind)
        assertEquals("DANA", t.toAccountHint)
        assertEquals("BCA", t.accountHint)
    }

    @Test fun skipsPromoOtpAndFailures() {
        assertTrue(PaymentParser.parseMessage("Dapatkan cashback hingga Rp50.000 pakai promo HEMAT!", "DANA", cats).isEmpty())
        assertTrue(PaymentParser.parseMessage("Kode OTP Anda 123456. Jangan berikan kepada siapapun", "BRImo", cats).isEmpty())
        assertTrue(PaymentParser.parseMessage("Pembayaran Rp25.000 gagal. Silakan coba lagi", "OVO", cats).isEmpty())
        assertTrue(PaymentParser.parseMessage("Saldo Anda saat ini Rp2.500.000", "BRImo", cats).isEmpty())
    }

    @Test fun indonesianDateText() {
        assertEquals("2026-11-05", PaymentParser.findDate("Tanggal 5 Nov 2026 14:30 WITA"))
        assertEquals("2026-08-17", PaymentParser.findDate("17 Agustus 2026"))
        assertEquals("2026-11-05", PaymentParser.findDate("05.11.2026"))
        assertNull(PaymentParser.findTime("05.11.2026"))
        assertEquals("14:30", PaymentParser.findTime("5 Nov 2026 14:30 WITA"))
    }

    // ---- images

    @Test fun indomaretReceipt() {
        val rows = listOf(
            "INDOMARET",
            "PT. INDOMARCO PRISMATAMA",
            "JL. AHMAD YANI NO. 12 WATAMPONE",
            "05.11.26-19:42 2.0.33 0001/KASIR/01",
            "BERAS PANDAN WANGI 5KG 1 68,000 68,000",
            "SABUN LIFEBUOY 1 9,900 9,900",
            "CHITATO SAPI PANGGANG 1 9,500 9,500",
            "HARGA JUAL : 87,400",
            "TOTAL : 87,400",
            "TUNAI : 100,000",
            "KEMBALI : 12,600",
            "PPN : 7,867"
        )
        val r = PaymentParser.parseImage(rows, cats)
        assertEquals(1, r.size)
        val t = r[0]
        assertEquals("expense", t.kind)
        assertEquals(87400.0, t.amount, 0.0)
        assertEquals("Indomaret", t.merchant)
        assertEquals("Groceries", t.category)
        assertEquals(3, t.items.size)
        assertEquals(68000.0, t.items[0].amount, 0.0)
        assertEquals("Groceries", t.items[0].category)
        assertEquals("Food & drinks", t.items[2].category)
    }

    @Test fun totalOnNextRow() {
        val rows = listOf("RM SEDERHANA", "Nasi Rendang 2 50.000", "Es Teh 2 10.000", "TOTAL", "60.000", "TUNAI 100.000")
        val t = PaymentParser.parseImage(rows, cats).single()
        assertEquals(60000.0, t.amount, 0.0)
        assertEquals("Rm Sederhana", t.merchant.replace("RM", "Rm"))
        assertEquals("Food & drinks", t.category)
    }

    @Test fun transferScreenshot() {
        val rows = listOf(
            "Transaksi Berhasil",
            "05 Nov 2026, 09:15:20 WITA",
            "Total Transaksi",
            "Rp 250.000",
            "Penerima",
            "SITI AMINAH",
            "BANK BRI",
            "No. Ref 202611050915",
            "Sumber Dana",
            "BRI 0123-01-0000"
        )
        val t = PaymentParser.parseImage(rows, cats).single()
        assertEquals(250000.0, t.amount, 0.0)
        assertEquals("2026-11-05", t.date)
        assertEquals("09:15", t.time)
        assertNotNull(t.reference)
    }

    @Test fun categoryGuesser() {
        assertEquals("Transport", CategoryGuesser.guess("SPBU Pertamina 74.921", cats))
        assertEquals("Phone & internet", CategoryGuesser.guess("Pembelian pulsa Telkomsel", cats))
        assertEquals("Bills", CategoryGuesser.guess("Token listrik PLN", cats))
        assertEquals("Health", CategoryGuesser.guess("Apotek K24", cats))
        assertEquals("Giving", CategoryGuesser.guess("Infaq Masjid Al Ikhlas", cats))
        assertNull(CategoryGuesser.guess("Xyz Corp", cats))
    }
}
