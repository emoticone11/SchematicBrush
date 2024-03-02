package com.westeroscraft.schematicbrush;

import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
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
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.command.tool.brush.Brush;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.forge.ForgePlayer;
import com.sk89q.worldedit.forge.ForgeWorldEdit;
import com.sk89q.worldedit.function.mask.BlockMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.session.PasteBuilder;
import com.sk89q.worldedit.util.Direction;
import com.sk89q.worldedit.util.io.Closer;
import com.sk89q.worldedit.util.io.file.FilenameException;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockTypes;

import com.westeroscraft.schematicbrush.commands.SCHMIGRATECommand;
import com.westeroscraft.schematicbrush.commands.SCHBRCommand;
import com.westeroscraft.schematicbrush.commands.SCHSETCommand;
import com.westeroscraft.schematicbrush.commands.SCHLISTCommand;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
	public static WorldEdit worldEdit;
	
	private File configDir;
	   
	public SchematicBrush() {
		// Register ourselves for server and other game events we are interested in
		MinecraftForge.EVENT_BUS.register(this);
	}

	private boolean ticking;
	private int ticks = 0;
	private List<Callable<Boolean>> pending = new ArrayList<Callable<Boolean>>();
	
	public void addJob(Callable<Boolean> job) {
		pending.add(job);
		ticking = true;
	}
	@SubscribeEvent
	public void countTicks(ServerTickEvent event) {
		if ((!ticking) || (event.phase != TickEvent.Phase.END))
			return;
		
		ticks++;
		if (ticks >= 5) {
			Iterator<Callable<Boolean>> iter = pending.iterator();
			while (iter.hasNext()) {
				Callable<Boolean> r = iter.next();
				Boolean rslt;
				try {
					rslt = r.call();
				} catch (Exception x) {
					rslt = Boolean.FALSE;
				}
				if (!rslt) {
					iter.remove();
				}
			}
			if (pending.size() == 0)
				ticking = false;
			
			ticks = 0;
		}
	}
	
	@SubscribeEvent
	public void onRegisterCommandEvent(RegisterCommandsEvent event) {
		CommandDispatcher<CommandSourceStack> commandDispatcher = event.getDispatcher();
		// SCHMIGRATECommand.register(this, commandDispatcher);
		SCHBRCommand.register(this, commandDispatcher);
		SCHSETCommand.register(this, commandDispatcher);
		SCHLISTCommand.register(this, commandDispatcher);
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
		worldEdit = WorldEdit.getInstance();
		log.info("Found worldedit " + we.getModInfo().getVersion());
	}
	
	public static void debugLog(String msg) {
		log.info(msg);
	}
}
