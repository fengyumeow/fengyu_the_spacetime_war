package com.example.playerinfo;

import com.example.playerinfo.data.HistoryFileManager;
import com.example.playerinfo.network.ModNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
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
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }

    private void onServerStarting(ServerStartingEvent event) {
        HistoryFileManager.init(event.getServer());
    }
}