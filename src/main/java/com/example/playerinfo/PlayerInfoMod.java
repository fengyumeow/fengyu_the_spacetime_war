package com.example.playerinfo;

import com.example.playerinfo.data.HistoryFileManager;
import com.example.playerinfo.network.ModNetwork;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(PlayerInfoMod.MOD_ID)
public final class PlayerInfoMod {

    public static final String MOD_ID =
            "fengyu_the_spacetime_war";

    public PlayerInfoMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);
        modEventBus.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }

    @SubscribeEvent
    private void onServerStarting(ServerStartingEvent event) {
        HistoryFileManager.init(event.getServer());
    }
}