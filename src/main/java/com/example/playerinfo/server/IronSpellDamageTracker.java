package com.example.playerinfo.server;

import com.example.playerinfo.PlayerInfoMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.scores.Objective;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = PlayerInfoMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class IronSpellDamageTracker {

    /*
     * 需要提前手动创建：
     * /scoreboard objectives add iron_damage dummy "铁魔法伤害"
     */
    public static final String OBJECTIVE_NAME =
            "iron_damage";

    private static final String SPELL_DAMAGE_SOURCE_CLASS =
            "io.redspace.ironsspellbooks.damage.SpellDamageSource";

    private IronSpellDamageTracker() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(
            LivingDamageEvent event
    ) {
        // 只在服务端记录
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        // 不记录被取消或者为零的伤害
        if (event.isCanceled()
                || event.getAmount() <= 0.0F) {
            return;
        }

        DamageSource damageSource =
                event.getSource();

        // 只记录 Iron's Spells 的法术伤害
        if (!isIronSpellDamage(damageSource)) {
            return;
        }

        /*
         * 获取真正的施法者。
         * 远程法术的直接实体可能是弹射物，
         * 但 getEntity() 通常是施法玩家。
         */
        if (!(damageSource.getEntity()
                instanceof ServerPlayer player)) {
            return;
        }

        // 不记录玩家对自己的法术伤害
        if (event.getEntity() == player) {
            return;
        }

        MinecraftServer server =
                player.getServer();

        if (server == null) {
            return;
        }

        ServerScoreboard scoreboard =
                server.getScoreboard();

        Objective objective =
                scoreboard.getObjective(OBJECTIVE_NAME);

        /*
         * 计分板不存在时直接停止。
         * 这里不会自动创建计分板。
         */
        if (objective == null) {
            return;
        }

        /*
         * 与原版 damage_dealt 保持相同的十分之一伤害单位。
         * 例如实际造成 5 点伤害，就增加 50 分。
         */
        int damageScore =
                Math.round(event.getAmount() * 10.0F);

        if (damageScore <= 0) {
            return;
        }

        scoreboard.getOrCreatePlayerScore(
                player.getScoreboardName(),
                objective
        ).add(damageScore);
    }

    static boolean isIronSpellDamage(
            DamageSource damageSource
    ) {
        Class<?> currentClass =
                damageSource.getClass();

        while (currentClass != null) {
            if (SPELL_DAMAGE_SOURCE_CLASS.equals(
                    currentClass.getName()
            )) {
                return true;
            }

            currentClass =
                    currentClass.getSuperclass();
        }

        return false;
    }
}
