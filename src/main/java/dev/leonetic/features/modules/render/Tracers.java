package dev.leonetic.features.modules.render;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.ColorUtil;
import dev.leonetic.util.render.RenderUtil;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;

public class Tracers extends  Module {
    private static final float LINE_WIDTH = 1.5f;
    private static final double START_OFFSET = 0.15;

    public final Setting<Boolean> showFriends       = bool("ShowFriends", true);
    public final Setting<ColorMode> colorMode       = mode("ColorMode", ColorMode.SINGLE);
    public final Setting<Color> singleColor         = color("Color", 120, 170, 210, 220);

    private int renderedPlayers;

    public Tracers() {
        super("Tracers", "Draws tracers from your view to other players.", Category.RENDER);

        singleColor.setVisibility(
                value -> colorMode.getValue() == ColorMode.SINGLE
        );
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        renderedPlayers = 0;
        if (nullCheck() || mc.options.hideGui) {
            return;
        }

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPosition = camera.position();

        Vec3 lookDirection = Vec3.directionFromRotation(
                camera.xRot(),
                camera.yRot()
        );

        Vec3 lineStart = cameraPosition.add(
                lookDirection.scale(START_OFFSET)
        );

        float partialTick = event.getDelta();

        for (Player player : mc.level.players()) {
            if (player == mc.player || player.isRemoved()) {
                continue;
            }

            if (!showFriends.getValue() && Homovore.friendManager.isFriend(player)) {
                continue;
            }

            Vec3 playerPosition = interpolate(player, partialTick);

            Vec3 lineEnd = playerPosition.add(0.0, player.getBoundingBox().getYsize() * 0.5, 0.0);

            double distance = cameraPosition.distanceTo(lineEnd);
            Color tracerColor = getTracerColor(player, distance);

            RenderUtil.drawLine(
                    lineStart,
                    lineEnd,
                    tracerColor,
                    LINE_WIDTH
            );

            renderedPlayers++;
        }
    }

    private Color getTracerColor(Player player, double distance) {
        return switch (colorMode.getValue()) {
            case RAINBOW -> withAlpha(
                    ColorUtil.rainbow(player.getId() * 40),
                    220
            );

            case DISTANCE -> getDistanceColor(distance);

            case SINGLE -> singleColor.getValue();
        };
    }

    private Color getDistanceColor(double distance) {
        double visibleDistance = Math.max(
                16.0,
                mc.options.renderDistance().get() * 16.0
        );

        float progress = (float) Mth.clamp(
                distance / visibleDistance,
                0.0,
                1.0
        );

        float hue = (1.0f - progress) / 3.0f;
        Color color = Color.getHSBColor(hue, 1.0f, 1.0f);

        return withAlpha(color, 220);
    }

    private static Vec3 interpolate(
            Player player,
            float partialTick
    ) {
        return new Vec3(
                Mth.lerp(
                        partialTick,
                        player.xOld,
                        player.getX()
                ),
                Mth.lerp(
                        partialTick,
                        player.yOld,
                        player.getY()
                ),
                Mth.lerp(
                        partialTick,
                        player.zOld,
                        player.getZ()
                )
        );
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(
                color.getRed(),
                color.getGreen(),
                color.getBlue(),
                alpha
        );
    }

    @Override
    public String getDisplayInfo() {
        return Integer.toString(renderedPlayers);
    }

    public enum ColorMode {
        RAINBOW,
        DISTANCE,
        SINGLE
    }
}
