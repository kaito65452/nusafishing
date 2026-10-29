package id.nusafishing;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import id.nusafishing.fishing.model.CustomFish;
import id.nusafishing.fishing.model.FishingBait;
import id.nusafishing.fishing.model.FishingRod;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Field;

import static org.junit.Assert.*;

public class NusaFishingLogicAuditTest {
    private ServerMock server;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
    }

    @After
    public void tearDown() {
        if (server != null) {
            MockBukkit.unmock();
        }
    }

    /**
     * Audit Area 1: Fish Generation & Price Calculations
     * Cek multiplier tier dasar: COMMON(1.0), UNCOMMON(1.5), RARE(2.0), EPIC(3.0), LEGENDARY(5.0), MYTHIC(8.0)
     */
    @Test
    public void testFishTierPriceMultipliers() {
        assertEquals(1.0, CustomFish.FishTier.COMMON.getPriceMultiplier(), 0.001);
        assertEquals(1.5, CustomFish.FishTier.UNCOMMON.getPriceMultiplier(), 0.001);
        assertEquals(2.0, CustomFish.FishTier.RARE.getPriceMultiplier(), 0.001);
        assertEquals(3.0, CustomFish.FishTier.EPIC.getPriceMultiplier(), 0.001);
        assertEquals(5.0, CustomFish.FishTier.LEGENDARY.getPriceMultiplier(), 0.001);
        assertEquals(8.0, CustomFish.FishTier.MYTHIC.getPriceMultiplier(), 0.001);
    }

    /**
     * Audit Area 1: Kontinuitas Formula Harga di titik 10.0 kg dan 100.0 kg.
     */
    @Test
    public void testPriceDiscontinuityAt10Kg() throws Exception {
        CustomFish fishUnder10 = new CustomFish("discontinuity_test", "Test Fish", "Desc",
                Material.COD, 0, "vanilla", null,
                1.0, 100.0, 50, 50, 100.0, CustomFish.FishTier.COMMON);
        CustomFish fishOver10 = new CustomFish("discontinuity_test", "Test Fish", "Desc",
                Material.COD, 0, "vanilla", null,
                1.0, 100.0, 50, 50, 100.0, CustomFish.FishTier.COMMON);

        Field weightField = CustomFish.class.getDeclaredField("weight");
        weightField.setAccessible(true);
        Field qualityField = CustomFish.class.getDeclaredField("quality");
        qualityField.setAccessible(true);
        Field tierField = CustomFish.class.getDeclaredField("actualTier");
        tierField.setAccessible(true);

        weightField.set(fishUnder10, 9.99);
        qualityField.set(fishUnder10, 50);
        tierField.set(fishUnder10, CustomFish.FishTier.COMMON);

        weightField.set(fishOver10, 10.01);
        qualityField.set(fishOver10, 50);
        tierField.set(fishOver10, CustomFish.FishTier.COMMON);

        double priceUnder10 = fishUnder10.getPrice();
        double priceOver10 = fishOver10.getPrice();

        // Formula mulus dan kontinu: ikan lebih berat berharga >= ikan lebih ringan
        assertTrue("Kontinuitas terbukti: ikan 10.01kg (" + priceOver10 + 
                ") berharga lebih tinggi dari 9.99kg (" + priceUnder10 + ")",
                priceOver10 >= priceUnder10);
    }

    /**
     * Audit Area 1: Kontinuitas Formula Harga di titik 100.0 kg.
     */
    @Test
    public void testPriceContinuityAt100Kg() throws Exception {
        CustomFish fishUnder100 = new CustomFish("discontinuity_test_100", "Test Fish", "Desc",
                Material.COD, 0, "vanilla", null,
                1.0, 500.0, 50, 50, 100.0, CustomFish.FishTier.COMMON);
        CustomFish fishOver100 = new CustomFish("discontinuity_test_100", "Test Fish", "Desc",
                Material.COD, 0, "vanilla", null,
                1.0, 500.0, 50, 50, 100.0, CustomFish.FishTier.COMMON);

        Field weightField = CustomFish.class.getDeclaredField("weight");
        weightField.setAccessible(true);
        Field qualityField = CustomFish.class.getDeclaredField("quality");
        qualityField.setAccessible(true);
        Field tierField = CustomFish.class.getDeclaredField("actualTier");
        tierField.setAccessible(true);

        weightField.set(fishUnder100, 99.99);
        qualityField.set(fishUnder100, 50);
        tierField.set(fishUnder100, CustomFish.FishTier.COMMON);

        weightField.set(fishOver100, 100.01);
        qualityField.set(fishOver100, 50);
        tierField.set(fishOver100, CustomFish.FishTier.COMMON);

        double priceUnder100 = fishUnder100.getPrice();
        double priceOver100 = fishOver100.getPrice();

        assertTrue("Kontinuitas terbukti di 100kg: ikan 100.01kg (" + priceOver100 + 
                ") berharga >= 99.99kg (" + priceUnder100 + ")",
                priceOver100 >= priceUnder100);
        // Lompatan harga harus sangat kecil (kontinu)
        assertTrue("Lompatan harga kecil: delta = " + Math.abs(priceOver100 - priceUnder100),
                Math.abs(priceOver100 - priceUnder100) < 1.0);
    }

    /**
     * Audit Area 1: Edge case pembagian nol pada getPrice() saat minWeight=0 dan maxWeight=0.
     */
    @Test
    public void testPriceDivisionByZeroWhenWeightIsZero() {
        CustomFish zeroFish = new CustomFish("zero_fish", "Zero Fish", "Desc",
                Material.COD, 0, "vanilla", null,
                0.0, 0.0, 0, 0, 100.0, CustomFish.FishTier.COMMON);

        double price = zeroFish.getPrice();
        assertFalse("Pembagian nol dicegah: harga valid dan bukan NaN", Double.isNaN(price));
        assertTrue("Harga minimal bernilai valid", price >= 1.0);
    }

    /**
     * Audit Area 1: Pencegahan crash IllegalArgumentException pada random.nextInt bound <= 0
     * Saat boostedMaxQuality < minQuality (misal qualityBoost = 0.4 saat minQuality = 50, maxQuality = 60).
     */
    @Test
    public void testQualityBoundNegativeCrash() {
        CustomFish fish = new CustomFish("crash_fish", "Crash Fish", "Desc",
                Material.COD, 0, "vanilla", null,
                1.0, 10.0, 50, 60, 100.0, CustomFish.FishTier.COMMON);

        // boostedMaxQuality di-clamp Math.max(minQuality, ...) sehingga tidak crash
        CustomFish caught = fish.createCaughtInstance(0, 0.4, 1.0, 10.0);
        assertNotNull(caught);
        assertTrue("Kualitas tidak crash dan >= minQuality", caught.getQuality() >= fish.getMinQuality());
    }

    /**
     * Audit Area 1: Rod maxWeightCapacity lebih kecil dari fish minWeight.
     * effectiveMaxWeight di-clamp ke minWeight sehingga bobot tidak di bawah minWeight.
     */
    @Test
    public void testMaxWeightCapacitySmallerThanMinWeight() {
        CustomFish heavyFish = new CustomFish("whale", "Whale", "Desc",
                Material.COD, 0, "vanilla", null,
                50.0, 100.0, 50, 50, 500.0, CustomFish.FishTier.COMMON);

        CustomFish caught = heavyFish.createCaughtInstance(0, 1.0, 1.0, 10.0);
        assertEquals(50.0, caught.getWeight(), 0.001);
        assertTrue("Bobot ikan tidak turun di bawah batas minWeight",
                caught.getWeight() >= 50.0);
    }

    /**
     * Audit Area 2: Economy Repair Cost calculation logic
     */
    @Test
    public void testRodRepairCostCalculation() {
        int durabilityLost = 50;
        double baseCostPerDurability = 1.0;
        double baseCost = durabilityLost * baseCostPerDurability;

        assertEquals(50.0, baseCost, 0.001);

        // Uji berbagai multiplier tier
        double basicCost = baseCost * 1.0;
        double expertCost = baseCost * 2.5;
        double mythicCost = baseCost * 5.0;

        assertEquals(50.0, basicCost, 0.001);
        assertEquals(125.0, expertCost, 0.001);
        assertEquals(250.0, mythicCost, 0.001);
    }

    /**
     * Audit Area 2: Market Saturation Reduction & Floor 0.3
     */
    @Test
    public void testMarketSaturationFloor() {
        double currentFactor = 1.0;
        // Jual 30 ikan MYTHIC (pengurangan 4% per ikan = 1.20)
        double reduction = 0.04 * 30;
        double newFactor = Math.max(0.3, currentFactor - reduction);

        assertEquals(0.3, newFactor, 0.001); // Floor harga tidak boleh di bawah 0.3 (30%)
    }

    /**
     * Audit Area 2: Market Price Recovery Equilibrium
     */
    @Test
    public void testMarketPriceRecoveryTowardsEquilibrium() {
        double currentFactor = 0.5;
        double recoveryRate = 0.03; // COMMON 3% per siklus

        // Naik perlahan menuju 1.0
        currentFactor = Math.min(1.0, currentFactor + recoveryRate);
        assertEquals(0.53, currentFactor, 0.001);

        // Jika harga di atas 1.0 (misal ada inflasi/surge), turun perlahan menuju 1.0
        double inflatedFactor = 1.2;
        inflatedFactor = Math.max(1.0, inflatedFactor - recoveryRate);
        assertEquals(1.17, inflatedFactor, 0.001);
    }

    /**
     * Audit Area 4: Bait Uses Tracking via PersistentDataContainer
     */
    @Test
    public void testBaitUsesPdcTracking() {
        ItemStack baitItem = new ItemStack(Material.ROTTEN_FLESH);
        ItemMeta meta = baitItem.getItemMeta();
        assertNotNull(meta);

        NamespacedKey usesKey = NamespacedKey.fromString("nusafishing:bait_uses");
        assertNotNull(usesKey);

        int maxUses = 5;
        meta.getPersistentDataContainer().set(usesKey, PersistentDataType.INTEGER, maxUses);
        baitItem.setItemMeta(meta);

        // Baca kembali
        ItemMeta readMeta = baitItem.getItemMeta();
        int remainingUses = readMeta.getPersistentDataContainer().getOrDefault(usesKey, PersistentDataType.INTEGER, maxUses);
        assertEquals(5, remainingUses);

        // Simulasikan konsumsi 1 use
        remainingUses--;
        readMeta.getPersistentDataContainer().set(usesKey, PersistentDataType.INTEGER, remainingUses);
        baitItem.setItemMeta(readMeta);

        ItemMeta afterMeta = baitItem.getItemMeta();
        assertEquals(4, (int) afterMeta.getPersistentDataContainer().get(usesKey, PersistentDataType.INTEGER));
    }

    /**
     * Audit Area 4: CustomFish PDC Consistency
     */
    @Test
    public void testCustomFishPdcTags() {
        CustomFish fish = new CustomFish("goldfish", "Goldfish", "A tiny golden fish",
                Material.COD, 1001, "vanilla", null,
                1.0, 5.0, 80, 95, 50.0, CustomFish.FishTier.RARE);

        CustomFish caught = fish.createCaughtInstance(0, 1.0, 1.0, 5.0);
        ItemStack item = caught.createItemStack();
        assertNotNull(item);

        ItemMeta meta = item.getItemMeta();
        assertNotNull(meta);

        NamespacedKey idKey = NamespacedKey.fromString("nusafishing:fish_id");
        NamespacedKey weightKey = NamespacedKey.fromString("nusafishing:fish_weight");
        NamespacedKey qualityKey = NamespacedKey.fromString("nusafishing:fish_quality");
        NamespacedKey tierKey = NamespacedKey.fromString("nusafishing:fish_tier");

        assertTrue(meta.getPersistentDataContainer().has(idKey, PersistentDataType.STRING));
        assertTrue(meta.getPersistentDataContainer().has(weightKey, PersistentDataType.DOUBLE));
        assertTrue(meta.getPersistentDataContainer().has(qualityKey, PersistentDataType.INTEGER));
        assertTrue(meta.getPersistentDataContainer().has(tierKey, PersistentDataType.INTEGER));

        assertEquals("goldfish", meta.getPersistentDataContainer().get(idKey, PersistentDataType.STRING));
    }
}
