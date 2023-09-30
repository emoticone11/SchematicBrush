package com.westeroscraft.schematicbrush;

import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkConstants;

import com.mojang.brigadier.CommandDispatcher;
import com.sk89q.worldedit.forge.ForgeWorldEdit;
import com.westeroscraft.schematicbrush.commands.SCHBRCommand;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(SchematicBrush.MOD_ID)
public class SchematicBrush {
	public static final String MOD_ID = "schematicbrush";

	// Directly reference a log4j logger.
	public static final Logger log = LogManager.getLogger();

	// Says where the client and server 'proxy' code is loaded.
	public static Proxy proxy = DistExecutor.safeRunForDist(() -> ClientProxy::new, () -> Proxy::new);

	public static Path modConfigPath;

	public static ModContainer we;
	public static ForgeWorldEdit wep;
	
	
	public SchematicBrush() {
		// Register ourselves for server and other game events we are interested in
		MinecraftForge.EVENT_BUS.register(this);

		Path configPath = FMLPaths.CONFIGDIR.get();

		modConfigPath = Paths.get(configPath.toAbsolutePath().toString(), MOD_ID);

		// Create the config folder
		try {
			Files.createDirectory(modConfigPath);
		} catch (FileAlreadyExistsException e) {
			// Do nothing
		} catch (IOException e) {
			log.error("Failed to create schematicbrush config directory", e);
		}
		// Set to be server only
		ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, 
        		()->new IExtensionPoint.DisplayTest(()->NetworkConstants.IGNORESERVERONLY, (remote, isServer)-> true));

		ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, Config.SPEC,
				MOD_ID + "/" + MOD_ID + ".toml");
	}

	private void doClientStuff(final FMLClientSetupEvent event) {
		// do something that can only be done on the client
		log.info("Got game settings {}", event.description());
	}

	@SubscribeEvent
	public void onRegisterCommandEvent(RegisterCommandsEvent event) {
	    CommandDispatcher<CommandSourceStack> commandDispatcher = event.getDispatcher();
		//PTimeCommand.register(commandDispatcher);
		//PWeatherCommand.register(commandDispatcher);
	    SCHBRCommand.register(commandDispatcher);
	}

	@SubscribeEvent
	public void serverStopping(ServerStoppingEvent event) {
		
	}
	
	public static void crash(Exception x, String msg) {
		throw new ReportedException(new CrashReport(msg, x));
	}

	public static void crash(String msg) {
		crash(new Exception(), msg);
	}

	public static class Config {
		public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
		public static final ForgeConfigSpec SPEC;
		public static final ForgeConfigSpec.BooleanValue debugLog;

		static {
			BUILDER.comment("Module options");
			BUILDER.push("debug");
			debugLog = BUILDER.comment("Debug logging").define("debugLog", false);
			BUILDER.pop();
			SPEC = BUILDER.build();
		}
	}

    @SubscribeEvent
    public void onServerStartingEvent(ServerStartingEvent event) {
    	ModContainer ourMod = ModList.get().getModContainerById(MOD_ID).get();
        log.info("SchematicBrush v" + ourMod.getModInfo().getVersion() + " loaded");

        Optional<? extends ModContainer> worldedit = ModList.get().getModContainerById("worldedit");
        if (!worldedit.isPresent()) {
            log.error("WorldEdit not found!!");
        	return;
        }
        we = worldedit.get();
        wep = (ForgeWorldEdit) we.getMod();        
        log.info("Found worldedit " + we.getModInfo().getVersion());
    }
    
    public static void debugLog(String msg) {
    	if (Config.debugLog.get()) { log.info(msg); }
    }
}
