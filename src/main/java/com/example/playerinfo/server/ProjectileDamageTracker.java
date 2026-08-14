package com.example.playerinfo.server;

import com.example.playerinfo.PlayerInfoMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = PlayerInfoMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class ProjectileDamageTracker {

    private ProjectileDamageTracker() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(
            LivingDamageEvent event
    ) {
        if (event.getEntity().level().isClientSide()
                || event.isCanceled()
                || event.getAmount() <= 0.0F) {
            return;
        }

        DamageSource damageSource = event.getSource();

        if (!(damageSource.getDirectEntity()
                instanceof Projectile projectile)) {
            return;
        }

        /*
         * Iron's Spells damage is already added to the total through
         * iron_damage, so do not count spell projectiles a second time.
         */
        if (IronSpellDamageTracker.isIronSpellDamage(
                damageSource
        )) {
            return;
        }

        ServerPlayer player = findPlayerOwner(
                damageSource,
                projectile
        );

        if (player == null || event.getEntity() == player) {
            return;
        }

        float actualDamage = Math.min(
                event.getAmount(),
                event.getEntity().getHealth()
        );

        int damageScore = Math.round(
                actualDamage * 10.0F
        );

        if (damageScore <= 0) {
            return;
        }

        /*
         * The vanilla statistic uses tenths of a damage point. Updating it
         * also updates every scoreboard objective based on damage_dealt.
         */
        player.awardStat(
                Stats.DAMAGE_DEALT,
                damageScore
        );
    }

    private static ServerPlayer findPlayerOwner(
            DamageSource damageSource,
            Projectile projectile
    ) {
        if (damageSource.getEntity()
                instanceof ServerPlayer player) {
            return player;
        }

        if (projectile.getOwner()
                instanceof ServerPlayer player) {
            return player;
        }

        return null;
    }
}
