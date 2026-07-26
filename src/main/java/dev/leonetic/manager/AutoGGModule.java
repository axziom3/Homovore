package dev.leonetic.features.modules.player;

import dev.leonetic.event.impl.entity.DeathEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class AutoGGModule extends Module {
    private boolean sentForCurrentDeath;

    public AutoGGModule() {
        super("AutoGG", "Sends gg when another player kills you.", Category.PLAYER);
    }

    @Override
    public void onEnable() {
        sentForCurrentDeath = false;
    }

    @Override
    public void onDisable() {
        sentForCurrentDeath = false;
    }

    @Override
    public void onTick() {
        // reset after user respawns
        if (!nullCheck() && !mc.player.isDeadOrDying()) {
            sentForCurrentDeath = false;
        }
    }

    @Subscribe
    private void onDeath(DeathEvent event) {
        if (nullCheck() || sentForCurrentDeath) {
            return;
        }

        if (event.getEntity() != mc.player) {
            return;
        }

        LivingEntity killCredit = mc.player.getKillCredit();

        if (!(killCredit instanceof Player killer) || killer == mc.player) {
            return;
        }

        if (mc.getConnection() == null) {
            return;
        }

        mc.getConnection().sendChat("gg");
        sentForCurrentDeath = true;
    }
}