package dev.leonetic.features.modules.combat;

import dev.leonetic.event.impl.entity.player.PreTickEvent;
import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.mixin.client.ClientLevelAccessor;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.ResultType;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class ExplodeAura extends Module {

    private static final int TIMEOUT_TICKS = 10;

    private BlockPos firstCandidate;
    private BlockPos secondCandidate;
    private int anchorHotbarSlot = -1;
    private int timeoutTicks;

    public ExplodeAura() {
        super("AutoExplode", "Charges and detonates respawn anchors you place.", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        resetSequence();
    }

    @Override
    public void onDisable() {
        resetSequence();
    }

    @Subscribe
    private void onPacketSend(PacketEvent.Send event) {
        if (nullCheck() || mc.player.isDeadOrDying()) return;
        if (mc.level.dimension().equals(Level.NETHER)) return;
        if (mc.player.containerMenu.containerId != 0) return;
        if (!(event.getPacket() instanceof ServerboundUseItemOnPacket packet)) return;
        if (packet.getHand() != InteractionHand.MAIN_HAND) return;
        if (!mc.player.getMainHandItem().is(Items.RESPAWN_ANCHOR)) return;

        BlockHitResult hit = packet.getHitResult();
        firstCandidate = hit.getBlockPos().immutable();
        secondCandidate = hit.getBlockPos().relative(hit.getDirection()).immutable();
        anchorHotbarSlot = mc.player.getInventory().getSelectedSlot();
        timeoutTicks = 0;
    }

    @Subscribe
    private void onPreTick(PreTickEvent event) {
        if (firstCandidate == null && secondCandidate == null) return;

        if (nullCheck()
                || mc.player.isDeadOrDying()
                || mc.level.dimension().equals(Level.NETHER)
                || mc.player.containerMenu.containerId != 0) {
            resetSequence();
            return;
        }

        BlockPos anchorPos = findPlacedAnchor();
        if (anchorPos == null) {
            if (++timeoutTicks > TIMEOUT_TICKS) resetSequence();
            return;
        }

        if (chargeAndDetonate(anchorPos)) {
            resetSequence();
        }
    }

    private BlockPos findPlacedAnchor() {
        if (isAnchor(firstCandidate)) return firstCandidate;
        if (isAnchor(secondCandidate)) return secondCandidate;
        return null;
    }

    private boolean isAnchor(BlockPos pos) {
        return pos != null && mc.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR);
    }

    private boolean chargeAndDetonate(BlockPos pos) {
        if (anchorHotbarSlot < 0 || anchorHotbarSlot > 8) return false;
        if (!InventoryUtil.cursor().isEmpty()) return false;

        Result glowstone = InventoryUtil.find(Items.GLOWSTONE, InventoryUtil.PLACE_SCOPE);
        if (!glowstone.found()) return false;

        ClientPacketListener conn = mc.getConnection();
        if (conn == null) return false;

        boolean movedGlowstone = false;
        boolean restoredGlowstone = false;
        int originalSlot = mc.player.getInventory().getSelectedSlot();
        BlockHitResult hit = anchorHit(pos);

        try {
            if (glowstone.type() == ResultType.INVENTORY
                    || glowstone.type() == ResultType.HOTBAR && glowstone.slot() != anchorHotbarSlot) {
                InventoryUtil.swapToHotbarSlot(glowstone.slot(), anchorHotbarSlot);
                movedGlowstone = true;
            } else if (glowstone.type() != ResultType.HOTBAR || glowstone.slot() != anchorHotbarSlot) {
                return false;
            }

            if (originalSlot != anchorHotbarSlot) {
                mc.player.getInventory().setSelectedSlot(anchorHotbarSlot);
                conn.send(new ServerboundSetCarriedItemPacket(anchorHotbarSlot));
            }

            sendUseItemOn(conn, hit);

            if (movedGlowstone) {
                InventoryUtil.swapToHotbarSlot(glowstone.slot(), anchorHotbarSlot);
                restoredGlowstone = true;
            }

            sendUseItemOn(conn, hit);
            conn.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
            return true;
        } finally {
            if (movedGlowstone && !restoredGlowstone) {
                InventoryUtil.swapToHotbarSlot(glowstone.slot(), anchorHotbarSlot);
            }
            if (mc.player.getInventory().getSelectedSlot() != originalSlot) {
                mc.player.getInventory().setSelectedSlot(originalSlot);
                conn.send(new ServerboundSetCarriedItemPacket(originalSlot));
            }
        }
    }

    private void sendUseItemOn(ClientPacketListener conn, BlockHitResult hit) {
        try (var handler = ((ClientLevelAccessor) mc.level).homovore$getBlockStatePredictionHandler().startPredicting()) {
            conn.send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hit, handler.currentSequence()));
        }
    }

    private BlockHitResult anchorHit(BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 eye = mc.player.getEyePosition(1.0f);
        double dx = eye.x - center.x;
        double dy = eye.y - center.y;
        double dz = eye.z - center.z;
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);

        Direction face;
        if (ay >= ax && ay >= az) {
            face = dy > 0 ? Direction.UP : Direction.DOWN;
        } else if (ax >= az) {
            face = dx > 0 ? Direction.EAST : Direction.WEST;
        } else {
            face = dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }

        Vec3 hitVec = center.add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        return new BlockHitResult(hitVec, face, pos, false);
    }

    private void resetSequence() {
        firstCandidate = null;
        secondCandidate = null;
        anchorHotbarSlot = -1;
        timeoutTicks = 0;
    }
}
