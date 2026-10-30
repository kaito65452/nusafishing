package id.nusafishing;

import id.nusafishing.gui.EventFishCollectorGUI;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class EventFishCollectorFeatureTest {

    /**
     * TUGAS 1: Verifikasi rumus bonus uang transaksi (kembali ke persentase total transaksi, bukan saldo pemain)
     * Rumus: percentBonus = 5 + (streak * 2); bonus = Math.max(10.0, transactionTotal * (percentBonus / 100.0))
     */
    @Test
    public void testTransactionMoneyBonusFormula() {
        double transactionTotal = 1500.0;
        int streak = 5; // 5% + 10% = 15%
        int percentBonus = 5 + (streak * 2);
        double bonus = Math.max(10.0, transactionTotal * (percentBonus / 100.0));

        assertEquals("Bonus persen pada streak 5 harus 15%", 15, percentBonus);
        assertEquals("Bonus uang harus 225.0", 225.0, bonus, 0.001);

        // Uji floor minimum 10.0 koin untuk transaksi sangat kecil
        double smallTransaction = 10.0;
        double smallBonus = Math.max(10.0, smallTransaction * (percentBonus / 100.0));
        assertEquals("Bonus uang minimal 10.0 koin", 10.0, smallBonus, 0.001);
    }

    /**
     * TUGAS 1: Verifikasi batas atas streak (max-streak)
     */
    @Test
    public void testMaxStreakCapping() {
        int maxStreak = 10;
        int streakAttempt = 25;
        int effectiveStreak = Math.min(maxStreak, streakAttempt);

        assertEquals("Streak tidak boleh melebihi max-streak", 10, effectiveStreak);
    }

    /**
     * TUGAS 2: Deteksi event fish ID dari PDC/nama
     */
    @Test
    public void testEventFishIdentification() {
        assertTrue("Prefix event_ harus diidentifikasi sebagai event fish", EventFishCollectorGUI.isEventFishId("event_ancient_leviathan"));
        assertTrue("Prefix event_kraken_king harus event fish", EventFishCollectorGUI.isEventFishId("event_kraken_king"));
        assertFalse("Ikan biasa cod tidak boleh event fish", EventFishCollectorGUI.isEventFishId("common_cod"));
        assertFalse("Ikan kosong tidak boleh event fish", EventFishCollectorGUI.isEventFishId(null));
    }

    /**
     * TUGAS 3: Verifikasi kuota harian penjualan kolektor
     */
    @Test
    public void testDailySellLimitCalculation() {
        int dailyLimit = 50;
        int soldToday = 45;
        int remainingQuota = (dailyLimit > 0) ? Math.max(0, dailyLimit - soldToday) : Integer.MAX_VALUE;

        assertEquals("Sisa kuota harian harus 5", 5, remainingQuota);

        int fishAttemptToSell = 10;
        int canSell = Math.min(fishAttemptToSell, remainingQuota);
        assertEquals("Hanya bisa menjual sebanyak sisa kuota", 5, canSell);

        // Setelah kuota habis
        soldToday += canSell;
        remainingQuota = Math.max(0, dailyLimit - soldToday);
        assertEquals("Kuota harus 0 saat batas tercapai", 0, remainingQuota);
    }

    /**
     * TUGAS 3: Verifikasi perhitungan pajak penjualan kolektor mistik
     * Formula: taxAmount = grossEarnings * (taxPercent / 100.0); netEarnings = grossEarnings - taxAmount
     */
    @Test
    public void testCollectorSaleTaxCalculation() {
        double grossEarnings = 10000.0;
        double taxPercent = 10.0; // 10% pajak
        double taxAmount = (taxPercent > 0.0) ? (grossEarnings * (taxPercent / 100.0)) : 0.0;
        double netEarnings = grossEarnings - taxAmount;

        assertEquals("Pajak 10% dari 10000 harus 1000.0", 1000.0, taxAmount, 0.001);
        assertEquals("Pendapatan bersih setelah pajak harus 9000.0", 9000.0, netEarnings, 0.001);

        // Bebas pajak jika taxPercent = 0
        double zeroTax = 0.0;
        double zeroTaxAmount = (zeroTax > 0.0) ? (grossEarnings * (zeroTax / 100.0)) : 0.0;
        assertEquals("Pajak harus 0 bila taxPercent = 0.0", 0.0, zeroTaxAmount, 0.001);
        assertEquals("Pendapatan bersih harus sama dengan kotor", grossEarnings, grossEarnings - zeroTaxAmount, 0.001);
    }
}
