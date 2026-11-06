package id.nusafishing.fisch.inventory;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Model data penyimpanan koleksi rod virtual milik pemain.
 * Thread-safe untuk pembacaan dan update.
 */
public class PlayerVirtualRods {

    private final UUID playerId;
    private final Set<String> ownedRods = new HashSet<>();
    private final Map<String, Integer> rodDurability = new HashMap<>();
    private String equippedRodId = null;
    private volatile boolean dirty = false;
    private volatile long lastModified = System.currentTimeMillis();

    public PlayerVirtualRods(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public synchronized boolean hasRod(String rodId) {
        if (rodId == null) return false;
        return ownedRods.contains(rodId.toLowerCase());
    }

    public synchronized boolean addRod(String rodId) {
        if (rodId == null) return false;
        boolean added = ownedRods.add(rodId.toLowerCase());
        if (added) {
            markDirty();
        }
        return added;
    }

    public synchronized boolean removeRod(String rodId) {
        if (rodId == null) return false;
        String key = rodId.toLowerCase();
        boolean removed = ownedRods.remove(key);
        rodDurability.remove(key);
        if (key.equalsIgnoreCase(equippedRodId)) {
            equippedRodId = null;
        }
        if (removed) {
            markDirty();
        }
        return removed;
    }

    public synchronized Set<String> getOwnedRods() {
        return Collections.unmodifiableSet(new HashSet<>(ownedRods));
    }

    public synchronized String getEquippedRodId() {
        return equippedRodId;
    }

    public synchronized void setEquippedRodId(String equippedRodId) {
        this.equippedRodId = equippedRodId != null ? equippedRodId.toLowerCase() : null;
        markDirty();
    }

    public synchronized int getDurability(String rodId, int defaultDurability) {
        if (rodId == null) return defaultDurability;
        return rodDurability.getOrDefault(rodId.toLowerCase(), defaultDurability);
    }

    public synchronized void setDurability(String rodId, int durability) {
        if (rodId == null) return;
        rodDurability.put(rodId.toLowerCase(), Math.max(0, durability));
        markDirty();
    }

    public synchronized Map<String, Integer> getAllDurabilities() {
        return Collections.unmodifiableMap(new HashMap<>(rodDurability));
    }

    public synchronized void loadFromData(Set<String> rods, String equipped, Map<String, Integer> durabilities) {
        this.ownedRods.clear();
        this.rodDurability.clear();
        if (rods != null) {
            for (String r : rods) {
                if (r != null) this.ownedRods.add(r.toLowerCase());
            }
        }
        if (durabilities != null) {
            for (Map.Entry<String, Integer> entry : durabilities.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    this.rodDurability.put(entry.getKey().toLowerCase(), entry.getValue());
                }
            }
        }
        this.equippedRodId = equipped != null ? equipped.toLowerCase() : null;
        this.dirty = false;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public void markDirty() {
        this.dirty = true;
        this.lastModified = System.currentTimeMillis();
    }

    public long getLastModified() {
        return lastModified;
    }
}
