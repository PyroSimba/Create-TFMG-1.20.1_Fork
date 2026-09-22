package com.drmangotea.tfmg.content.machinery.oil_processing.pumpjack.pumpjack.base;

import com.drmangotea.tfmg.TFMG;
import com.drmangotea.tfmg.base.TFMGUtils;
import com.drmangotea.tfmg.content.machinery.oil_processing.pumpjack.pumpjack.crank.PumpjackCrankBlockEntity;
import com.drmangotea.tfmg.content.machinery.oil_processing.pumpjack.pumpjack.hammer.PumpjackBlockEntity;
import com.drmangotea.tfmg.registry.TFMGFluids;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import javax.annotation.Nonnull;
import java.util.List;

public class PumpjackBaseBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public PumpjackBlockEntity controllerHammer;
    public boolean isRunning = false;
    public int miningRate = 0;
    protected LazyOptional<IFluidHandler> fluidCapability;
    public FluidTank tank;

    /** Null when pumpjack's chunk has no oil. */
    public ChunkPos depositChunk;
    private boolean depositChecked = false;

    public PumpjackBaseBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        tank = createInventory();
        fluidCapability = LazyOptional.of(() -> tank);
    }

    @Override
    public void tick() {
        super.tick();

        if (controllerHammer != null)
            if (!(level.getBlockEntity(controllerHammer.getBlockPos()) instanceof PumpjackBlockEntity))
                controllerHammer = null;
        if (controllerHammer != null)
            if (controllerHammer.base == null)
                controllerHammer = null;

        if (controllerHammer != null)
            if (!controllerHammer.isRunning())
                controllerHammer = null;

        if (controllerHammer == null)
            return;
        isRunning = controllerHammer.isRunning();

        if (!isRunning) {
            miningRate = 0;
            return;
        }

        // Chunk oil is deterministic (seed + chunk pos), so this only needs to run once
        // per pumpjack rather than on a recurring timer like the old block-scanning
        // version did.
        // Deferred to first tick rather than the constructor/read, since level isn't
        // guaranteed attached yet during deserialization.
        if (!depositChecked) {
            depositChecked = true;
            findDeposit();
        }

        PumpjackCrankBlockEntity crank = null;
        if (controllerHammer.crank != null)
            crank = controllerHammer.crank;

        if (crank == null)
            return;
        miningRate = (int) Math.abs(crank.getMachineInputSpeed() * (crank.heightModifier));
        process();
    }

    public void findDeposit() {
        ChunkPos chunkPos = new ChunkPos(this.getBlockPos());
        depositChunk = TFMG.DEPOSITS.hasOil(level, chunkPos) ? chunkPos : null;
    }

    public void process() {
        if (depositChunk == null)
            return;

        if (tank.getFluidAmount() + miningRate > tank.getCapacity())
            return;

        int remaining = TFMG.DEPOSITS.getRemaining(level, depositChunk);
        if (remaining <= 0)
            return;

        int amountToPump = Math.min(miningRate, remaining);
        int amountPumped = tank.fill(new FluidStack(TFMGFluids.CRUDE_OIL.getSource(), amountToPump),
                IFluidHandler.FluidAction.EXECUTE);
        sendData();

        if (amountPumped == 0)
            return;

        if (!level.isClientSide)
            TFMG.DEPOSITS.consume(level, depositChunk, amountPumped);
    }

    public void setControllerHammer(PumpjackBlockEntity controllerHammer) {
        this.controllerHammer = controllerHammer;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    protected SmartFluidTank createInventory() {
        return new SmartFluidTank(8000, this::onFluidStackChanged) {
            @Override
            public boolean isFluidValid(FluidStack stack) {
                return stack.getFluid().isSame(TFMGFluids.CRUDE_OIL.getSource());
            }
        };
    }

    protected void onFluidStackChanged(FluidStack newFluidStack) {
        sendData();
        setChanged();
    }

    @Override
    @SuppressWarnings("removal")
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.translate("goggles.pumpjack_info")
                .forGoggles(tooltip);
        if (depositChunk == null) {
            CreateLang.translate("goggles.zero")
                    .style(ChatFormatting.DARK_RED)
                    .forGoggles(tooltip, 1);
        }

        TFMGUtils.createFluidTooltip(this, tooltip);
        return true;
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);
        tank.readFromNBT(compound.getCompound("TankContent"));
    }

    @Override
    public void write(CompoundTag compound, boolean clientPacket) {
        compound.put("TankContent", tank.writeToNBT(new CompoundTag()));
        super.write(compound, clientPacket);
    }

    @Nonnull
    @Override
    @SuppressWarnings("removal")
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER)
            return fluidCapability.cast();
        return super.getCapability(cap, side);
    }
}