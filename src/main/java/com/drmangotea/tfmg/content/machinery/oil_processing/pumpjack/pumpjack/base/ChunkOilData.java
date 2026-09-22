package com.drmangotea.tfmg.content.machinery.oil_processing.pumpjack.pumpjack.base;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;

/*
    Decides based on world seed & chunk position whether a chunk will have an oil deposit.
*/
public class ChunkOilData {

    /* 1/50 chance that a given chunk has oil. */
    private static final int PRESENCE_CHANCE = 50;

    public static long getSeedSafely(LevelAccessor level) {
        if (level instanceof ServerLevelAccessor serverLevel) {
            return serverLevel.getLevel().getSeed();
        }
        return 0L;
    }

    public static boolean hasOil(LevelAccessor level, ChunkPos pos) {
        return hasOilFromSeed(getSeedSafely(level), pos);
    }

    public static boolean hasOilFromSeed(long levelSeed, ChunkPos pos) {
        return chunkRandom(levelSeed, pos).nextInt(PRESENCE_CHANCE) == 0;
    }

    /*
     * Random starting reserve size for a chunk.
     * Meant for if finite reserves are enabled.
     */
    public static int rollMaxReserves(LevelAccessor level, ChunkPos pos, int min, int max) {
        RandomSource random = chunkRandom(getSeedSafely(level), pos);
        random.nextInt(PRESENCE_CHANCE);
        return min + random.nextInt(Math.max(1, max - min));
    }

    private static RandomSource chunkRandom(long levelSeed, ChunkPos pos) {
        long seed = levelSeed;
        seed = seed * 341873128712L + pos.x;
        seed = seed * 132897987541L + pos.z;
        return RandomSource.create(seed);
    }
}
