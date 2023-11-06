package com.westeroscraft.schematicbrush.commands;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.enginehub.piston.exception.StopExecutionException;

import com.mojang.brigadier.CommandDispatcher;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalConfiguration;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extension.platform.permission.ActorSelectorLimits;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;
import com.sk89q.worldedit.util.io.Closer;
import com.sk89q.worldedit.world.World;
import com.westeroscraft.schematicbrush.SchematicBrush;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionSelector;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;


public class SCHMIGRATECommand {
	private static SchematicBrush sb;
	
	public static void register(SchematicBrush mod, CommandDispatcher<CommandSourceStack> source) {
		sb = mod;
		SchematicBrush.log.info("Register schmigrate");
		source.register(Commands.literal("schmigrate").executes((ctx) -> {
			return schMigrate(ctx.getSource());
		}));

	}

	private static class SchRecord {
		public String fname;
		int originX, originY, originZ;
		int minX, minY, minZ;
		int maxX, maxY, maxZ;
		public String toString() {
			return String.format("%s: origin=%d:%d:%d, min=%d:%d:%d, max=%d:%d:%d", fname, originX, originY, originZ,
				minX, minY, minZ, maxX, maxY, maxZ);					
		}
	};
	
	public static int schMigrate(CommandSourceStack source) {
		if (source.getEntity() instanceof ServerPlayer) {
			ServerPlayer player = (ServerPlayer) source.getEntity();
			// Read schapplyall.txt
			File logf = new File("schapplyall.txt");
			if (!logf.canRead()) {
				source.sendFailure(new TextComponent("Cannot find schapplyall.txt"));
				return 1;
			}
			List<SchRecord> list = new ArrayList<SchRecord>();
			try (BufferedReader rdr = new BufferedReader(new FileReader(logf))) {
				String line;
				while ((line = rdr.readLine()) != null) {
					int idx = line.indexOf(':');	// Find end of name
					String fname = line.substring(0, idx);
					// Prune extension
					idx = fname.lastIndexOf('.');
					fname = fname.substring(0, idx);
					line = line.substring(idx+1).strip();	// Remainder of line
					String tok[] = line.split(",");	// Split on commas
					if (tok.length >= 3) {
						SchRecord rec = new SchRecord();
						rec.fname = fname;
						String origin[] = tok[0].substring(tok[0].indexOf('=')+1).split(":");
						rec.originX = Integer.parseInt(origin[0]);
						rec.originY = Integer.parseInt(origin[1]);
						rec.originZ = Integer.parseInt(origin[2]);
						String min[] = tok[1].substring(tok[1].indexOf('=')+1).split(":");
						rec.minX = Integer.parseInt(min[0]);
						rec.minY = Integer.parseInt(min[1]);
						rec.minZ = Integer.parseInt(min[2]);
						String max[] = tok[2].substring(tok[2].indexOf('=')+1).split(":");
						rec.maxX = Integer.parseInt(max[0]);
						rec.maxY = Integer.parseInt(max[1]);
						rec.maxZ = Integer.parseInt(max[2]);
						list.add(rec);
						//SchematicBrush.log.info(rec);
					}					
				}
				SchematicBrush.log.info(String.format("Loaded %d schematic records", list.size()));
				ApplyAllJob job = new ApplyAllJob();
				job.session = SchematicBrush.wep.getSession(player);
				job.recs = list;
				job.player = player;
				job.actor = ForgeAdapter.adaptPlayer(player);
				job.world = ForgeAdapter.adapt(player.getLevel());
		        job.editSession = job.session.createEditSession(job.actor);
				sb.addJob(job);
			} catch (IOException iox) {
				source.sendFailure(new TextComponent("Error reading schapplyall.txt"));
				return 1;				
			}
			source.sendSuccess(new TextComponent("Migrate started"), true);
		} else {
			source.sendFailure(new TextComponent("Cannot be used by console"));
		}
		
		return 1;
	}
	public static class ApplyAllJob implements Callable<Boolean> {
		public LocalSession session;
		public EditSession editSession;
		public World world;
		List<SchRecord> recs;
		int idx = 0;
		Actor actor;
		ServerPlayer player;
		
		@Override
		public Boolean call() throws Exception {
			if (idx >= recs.size()) {
				return Boolean.FALSE;
			}
			SchRecord rec = recs.get(idx);
			idx++;

			//player.setPos(new Vec3(rec.originX, rec.originY, rec.originY));
			
			LocalConfiguration config = SchematicBrush.worldEdit.getConfiguration();

	        File dir = SchematicBrush.worldEdit.getWorkingDirectoryPath(config.saveDir).toFile();

	        ClipboardFormat format = ClipboardFormats.findByAlias("schem");
	        if (format == null) {
				actor.print("Format error");
	            return Boolean.FALSE;
	        }

	        File f = sb.worldEdit.getSafeSaveFile(actor, dir, rec.fname, format.getPrimaryFileExtension());

	        // Create parent directories
	        File parent = f.getParentFile();
	        if (parent != null && !parent.exists()) {
	            if (!parent.mkdirs()) {
	                throw new StopExecutionException(TranslatableComponent.of(
	                        "worldedit.schematic.save.failed-directory"));
	            }
	        }
			RegionSelector rsel = session.getRegionSelector(world);
			rsel.clear();
			rsel.selectPrimary(BlockVector3.at(rec.minX, rec.minY, rec.minZ), ActorSelectorLimits.forActor(actor));
			rsel.selectSecondary(BlockVector3.at(rec.maxX, rec.maxY, rec.maxZ), ActorSelectorLimits.forActor(actor));	
			rsel.learnChanges();
			Region region = rsel.getRegion();
	        BlockArrayClipboard cb = new BlockArrayClipboard(region);
	        cb.setOrigin(BlockVector3.at(rec.originX, rec.originY, rec.originZ));
	        
	        ForwardExtentCopy copy = new ForwardExtentCopy(editSession, region, cb, region.getMinimumPoint());
	        Operations.completeLegacy(copy);
	        ClipboardHolder holder = new ClipboardHolder(cb);
	        SchematicBrush.log.info(cb.getRegion());
	        SchematicBrush.log.info(cb.getDimensions());
	        session.setClipboard(holder);

            try (Closer closer = Closer.create()) {
                FileOutputStream fos = closer.register(new FileOutputStream(f));
                BufferedOutputStream bos = closer.register(new BufferedOutputStream(fos));
                ClipboardWriter writer = closer.register(format.getWriter(bos));
                writer.write(cb);
            } catch (IOException e) {
                f.delete();
				actor.print("Schematic save error - " + e.getMessage());
				return Boolean.FALSE;
            }
            actor.print(String.format("Wrote %s (%d bytes)", f.getAbsolutePath(), f.length()));

            return Boolean.TRUE;
		}
		
	}
}
