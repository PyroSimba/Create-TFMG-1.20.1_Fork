package com.drmangotea.tfmg.content.machinery.oil_processing.pumpjack.pumpjack.base;

import com.drmangotea.tfmg.config.TFMGConfigs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

public class DepositManager {

    private DepositSavedData savedData;

    public boolean hasOil(Level level, ChunkPos pos) {
        return ChunkOilData.hasOil(level, pos);
    }

    /* Returns Integer.MAX_VALUE when infinite deposits are enabled in config. */
    public int getRemaining(Level level, ChunkPos pos) {
        if (TFMGConfigs.common().worldgen.infiniteDeposits.get())
            return Integer.MAX_VALUE;

        long key = pos.toLong();
        Integer stored = savedData == null ? null : savedData.getRemaining(key);
        if (stored != null)
            return stored;

        return ChunkOilData.rollMaxReserves(level, pos,
                TFMGConfigs.common().worldgen.depositMinReserves.get(),
                TFMGConfigs.common().worldgen.depositMaxReserves.get());
    }

    public void consume(Level level, ChunkPos pos, int amount) {
        if (TFMGConfigs.common().worldgen.infiniteDeposits.get() || savedData == null)
            return;

        int remaining = getRemaining(level, pos) - amount;
        savedData.setRemaining(pos.toLong(), Math.max(0, remaining));
    }

    public void levelLoaded(LevelAccessor level) {
        MinecraftServer server = level.getServer();
        if (server == null || server.overworld() != level)
            return;
        savedData = DepositSavedData.load(server);
    }
}