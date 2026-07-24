package dev.leonetic.features.modules.funny;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.PreTickEvent;
import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.player.ChatUtil;
import dev.leonetic.util.render.RenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AutoDick extends Module {

    private final Setting<Boolean>  render          = bool("Render", true);
    private final Setting<Float>    fadeTime        = num("FadeTime", 1.0f, 0.05f, 2.0f);
    private final Setting<Color>    fillColor       = color("FillColor", 85, 0, 255, 44);
    private final Setting<Color>    outlineColor    = color("OutlineColor", 85, 0, 255, 44);

    private final List<BlockPos>       dickBlocks   = new ArrayList<>();
    private BlockPos bottomLeftBlock = null;
    private BlockPos bottomRightBlock = null;
    private BlockPos middleBlock = null;
    private BlockPos topBlock = null;
    private final Map<BlockPos, Long>  renderMap    = new HashMap<>();

    public AutoDick() {
        super("AutoDick", "you know what you're doing", Category.WORLD);
    }

    @Override
    public void onEnable() {
        if (nullCheck()) {
            disable();
            return;
        }

        dickBlocks.clear();

        int obsidianCount = 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == Items.OBSIDIAN) {
                obsidianCount += mc.player.getInventory().getItem(i).getCount();
            }
        }

        if (obsidianCount < 4) {
            ChatUtil.sendMessage(Component.literal("[AutoDick] dipshit get enough obby (4)"));
            disable();
            return;
        }

        Direction forward    = mc.player.getDirection();
        Direction backward   = forward.getOpposite();
        Direction right      = forward.getClockWise();
        BlockPos standingPos = mc.player.blockPosition();
        BlockPos below  = standingPos.below();

        double blockHeight;
        try {
            blockHeight = mc.level.getBlockState(below)
                    .getCollisionShape(mc.level, below).max(Direction.Axis.Y);
        } catch (Exception e) {
            blockHeight = 1.0;
        }
        if (blockHeight < 1.0) standingPos = standingPos.above();

        BlockPos base = standingPos.relative(backward, 2);

        List<BlockPos> frame = new ArrayList<>();
        BlockPos bottomLeft = base.relative(right, -1);
        BlockPos bottomRight = base.relative(right, 1);
        BlockPos middle = base.above();
        BlockPos top = base.above(2);
        bottomLeftBlock = bottomLeft;
        bottomRightBlock = bottomRight;
        middleBlock = middle;
        topBlock = top;

        frame.add(bottomLeft);
        frame.add(bottomRight);
        frame.add(middle);
        frame.add(top);

        long obsidianMatches = frame.stream()
                .filter(pos -> mc.level.getBlockState(pos).getBlock().asItem() == Items.OBSIDIAN)
                .count();
        if (obsidianMatches >= frame.size()) {
            ChatUtil.sendMessage(Component.literal("[AutoDick] already a dick here"));
            disable();
            return;
        }

        boolean obstructed = frame.stream().anyMatch(blockPos ->
                !mc.level.getBlockState(blockPos).canBeReplaced()
                    && mc.level.getBlockState(blockPos).getBlock().asItem() != Items.OBSIDIAN);
        if (obstructed) {
            ChatUtil.sendMessage(Component.literal("[AutoDick] dick got fuuuucked. move away from the dicks u fag"));
            disable();
            return;
        }

        dickBlocks.addAll(frame);
    }

    @Override
    public void onDisable() {
        Homovore.placementManager.removeQueuedFor(dickBlocks::contains);
        dickBlocks.clear();
        bottomLeftBlock = null;
        bottomRightBlock = null;
        middleBlock = null;
        topBlock = null;
        renderMap.clear();
    }

    @Subscribe
    private void onPreTick(PreTickEvent event) {
        if (nullCheck() || dickBlocks.isEmpty()) return;

        boolean allDone = true;
        for (BlockPos pos : dickBlocks) {
            if (mc.level.getBlockState(pos).getBlock().asItem() != Items.OBSIDIAN) {
                allDone = false;
                break;
            }
        }

        if (allDone) {
            ChatUtil.sendMessage(Component.literal("[AutoDick] dick came ;)"));
            disable();
            return;
        }

        Result obsidian = findHotbar(Items.OBSIDIAN);
        if (obsidian == null) return;

        Vec3 eye = mc.player.getEyePosition();
        long now = System.currentTimeMillis();
        for (BlockPos pos : dickBlocks) {
            var state = mc.level.getBlockState(pos);
            if (state.getBlock().asItem() == Items.OBSIDIAN) continue;
            if (Vec3.atCenterOf(pos).distanceTo(eye) > 2.650) {
                ChatUtil.sendMessage(Component.literal("[AutoDick] hm, you straight or sum? standing a bit far away to be able to place this dick bruh"));
                disable();
                return;
            }
            if (state.canBeReplaced()) {
                BlockPos queuedPos = pos;
                Direction face = null;
                if (pos.equals(bottomLeftBlock) || pos.equals(bottomRightBlock)) {
                    face = Direction.DOWN;
                } else if (pos.equals(middleBlock)) {
                    queuedPos = topBlock;
                    face = Direction.DOWN;
                } else if (pos.equals(topBlock) && !isObsidian(middleBlock)) {
                    continue;
                } else if (pos.equals(topBlock)) {
                    face = Direction.DOWN;
                }

                boolean queued = face == null
                        ? Homovore.placementManager.enqueue(queuedPos, obsidian.slot())
                        : Homovore.placementManager.enqueue(queuedPos, face, obsidian.slot());
                if (queued) {
                    renderMap.put(pos, now);
                }
            } else {
                mc.gameMode.startDestroyBlock(pos, Direction.UP);
                mc.player.swing(InteractionHand.MAIN_HAND);
            }
        }

        long fadeMs = (long) (fadeTime.getValue() * 1000);
        renderMap.entrySet().removeIf(e -> now - e.getValue() > fadeMs);
    }

    private Result findHotbar(net.minecraft.world.item.Item item) {
        Result r = InventoryUtil.find(item, InventoryUtil.PLACE_SCOPE);
        return r.found() ? r : null;
    }

    private boolean isObsidian(BlockPos pos) {
        return pos != null && mc.level.getBlockState(pos).getBlock().asItem() == Items.OBSIDIAN;
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!render.getValue()) return;

        long now = System.currentTimeMillis();
        double fadeMs = fadeTime.getValue() * 1000.0;

        for (Map.Entry<BlockPos, Long> entry : renderMap.entrySet()) {
            long age = now - entry.getValue();
            if (age > fadeMs) continue;

            double t = age / fadeMs;

            Color fc = fillColor.getValue();
            Color oc = outlineColor.getValue();

            RenderUtil.drawBoxFilled(event.getMatrix(), entry.getKey(),
                    withAlpha(fc, (int) (fc.getAlpha() * (1 - t))));
            RenderUtil.drawBox(event.getMatrix(), entry.getKey(),
                    withAlpha(oc, (int) (oc.getAlpha() * (1 - t))), 1.0f);
        }
    }

    private static Color withAlpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Mth.clamp(a, 0, 255));
    }
}
