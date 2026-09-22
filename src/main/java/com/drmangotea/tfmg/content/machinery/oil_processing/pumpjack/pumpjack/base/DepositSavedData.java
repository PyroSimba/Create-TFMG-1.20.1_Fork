package com.drmangotea.tfmg.content.machinery.oil_processing.pumpjack.pumpjack.base;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/*
    Persists remaining reserves for chunks that have been drawn down from their rolled maximum.
    Used when finite reserves are enabled.
    A chunk with no entry here is assumed to be full of oil.
*/
public class DepositSavedData extends SavedData {

    private final Map<Long, Integer> remainingReserves = new HashMap<>();

    public Integer getRemaining(long chunkKey) {
        return remainingReserves.get(chunkKey);
    }

    public void setRemaining(long chunkKey, int amount) {
        remainingReserves.put(chunkKey, amount);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag compound) {
        compound.putInt("Count", remainingReserves.size());
        int i = 0;
        for (Map.Entry<Long, Integer> entry : remainingReserves.entrySet()) {
            compound.putLong("Chunk" + i, entry.getKey());
            compound.putInt("Remaining" + i, entry.getValue());
            i++;
        }
        return compound;
    }

    private static DepositSavedData load(CompoundTag compound) {
        DepositSavedData data = new DepositSavedData();
        int count = compound.getInt("Count");
        for (int i = 0; i < count; i++)
            data.remainingReserves.put(compound.getLong("Chunk" + i), compound.getInt("Remaining" + i));
        return data;
    }

    public static DepositSavedData load(MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(DepositSavedData::load, DepositSavedData::new, "tfmg_chunk_deposits");
    }
}