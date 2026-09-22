package com.drmangotea.tfmg.content.machinery.oil_processing;

import com.drmangotea.tfmg.TFMG;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public class OilHammerItem extends Item {
    public OilHammerItem(Properties p_41383_) {
        super(p_41383_);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ChunkPos chunkPos = new ChunkPos(context.getClickedPos());

        if (!TFMG.DEPOSITS.hasOil(level, chunkPos)) {
            if (level.isClientSide && player != null)
                player.displayClientMessage(CreateLang.translateDirect("oil_hammer.no_deposit")
                        .withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }

        int remaining = TFMG.DEPOSITS.getRemaining(level, chunkPos);
        if (level.isClientSide && player != null) {
            if (remaining == Integer.MAX_VALUE)
                player.displayClientMessage(CreateLang.translateDirect("oil_hammer.reserves_infinite")
                        .withStyle(ChatFormatting.YELLOW), true);
            else
                player.displayClientMessage(CreateLang.translateDirect("oil_hammer.reserves", remaining)
                        .withStyle(ChatFormatting.YELLOW), true);
        }

        return InteractionResult.SUCCESS;
    }
}