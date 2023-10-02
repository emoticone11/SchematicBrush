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
import com.westeroscraft.schematicbrush.commands.SCHBRCommand;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Vector;
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
	

	private File configDir;
	
	public static final int DEFAULT_WEIGHT = -1;

    public static enum Flip {
        NONE, NS, EW, RANDOM;
    }
    public static enum Rotation {
        ROT0(0), ROT90(90), ROT180(180), ROT270(270), RANDOM(-1);
        
        final int deg;
        Rotation(int deg) {
            this.deg = deg;
        }
    }
    public static enum Placement {
        CENTER, BOTTOM, DROP
    }
    
    private static Random rnd = new Random();
    
    public static class SchematicDef {
        public String name;
        public String format;
        public Rotation rotation;
        public Flip flip;
        public int weight;      // If -1, equal weight with other -1 schematics
        public int offset;
        @Override
        public boolean equals(Object o) {
            if (o instanceof SchematicDef) {
                SchematicDef sd = (SchematicDef) o;
                return sd.name.equals(this.name) && (sd.rotation == this.rotation) && (sd.flip == this.flip) && (sd.weight == this.weight) && (sd.offset == this.offset);
            }
            return false;
         }
        @Override
        public String toString() {
            String n = this.name;
            if (format != null)
                n += "#" + format;
            if ((rotation != Rotation.ROT0) || (flip != Flip.NONE)) {
                n += "@";
                if (rotation == Rotation.RANDOM)
                    n += '*';
                else
                    n += (90 * rotation.ordinal());
            }
            if (flip == Flip.RANDOM) {
                n += '*';
            }
            else if (flip == Flip.NS) {
                n += 'N';
            }
            else if (flip == Flip.EW) {
                n += 'E';
            }
            if (weight >= 0) {
                n += ":" + weight;
            }
            if (offset != 0) {
                n += "^" + offset;
            }
            return n;
        }
        public Rotation getRotation() {
            if (rotation == Rotation.RANDOM) {
                return Rotation.values()[rnd.nextInt(4)];
            }
            return rotation;
        }
        public Flip getFlip() {
            if (flip == Flip.RANDOM) {
                return Flip.values()[rnd.nextInt(3)];
            }
            return flip;
        }
        public int getOffset() {
            return offset;
        }
    }
    
    public static class SchematicSet {
        public String name;
        public String desc;
        public List<SchematicDef> schematics;
        public SchematicSet(String n, String d, List<SchematicDef> sch) {
            this.name = n;
            this.desc = (d == null)?"":d;
            this.schematics = (sch == null)?(new ArrayList<SchematicDef>()):sch;
        }
        public int getTotalWeights() {
            int wt = 0;
            for (SchematicDef sd : schematics) {
                if (sd.weight > 0)
                    wt += sd.weight;
            }
            return wt;
        }
        public int getEqualWeightCount() {
            int cnt = 0;
            for (SchematicDef sd : schematics) {
                if (sd.weight <= 0) {
                    cnt++;
                }
            }
            return cnt;
        }
        public SchematicDef getRandomSchematic() {
            int total = getTotalWeights();
            int cnt = getEqualWeightCount();
            int rndval = 0;
            // If any fixed weights
            if (total > 0) {
                // If total fixed more than 100, or equal weight count is zero
                if ((total > 100) || (cnt == 0)) {
                    rndval = rnd.nextInt(total);    // Random from 0 to total-1
                }
                else {
                    rndval = rnd.nextInt(100);      // From 0 to 100 
                }
                if (rndval < total) {   // Fixed weight match
                    for (SchematicDef def : schematics) {
                        if (def.weight > 0) {
                            rndval -= def.weight;
                            if (rndval < 0) {   // Match?
                                return def;
                            }
                        }
                    }
                }
            }
            if (cnt > 0) {
                rndval = rnd.nextInt(cnt);  // Pick from equal weight values
                for (SchematicDef def : schematics) {
                    if (def.weight < 0) {
                        rndval--;
                        if (rndval < 0) {
                            return def;
                        }
                    }
                }
            }
            return null;
        }
    }
    private HashMap<String, SchematicSet> sets = new HashMap<String, SchematicSet>();
    
    
    public class SchematicBrushInstance implements Brush {
        SchematicSet set;
        ServerPlayer player;
        boolean skipair;
        boolean replaceall;
        int yoff;
        Placement place;
        
        @Override
        public void build(EditSession editsession, BlockVector3 pos, com.sk89q.worldedit.function.pattern.Pattern mat, double size) throws MaxChangedBlocksException {
            SchematicDef def = set.getRandomSchematic();    // Pick schematic from set
            if (def == null) return;
            LocalSession sess = wep.getSession(player);
            int[] minY = new int[1];
            ForgePlayer actor = ForgeAdapter.adaptPlayer(player);
            String schfilename = loadSchematicIntoClipboard(actor, sess, def.name, def.format, minY);
            if (schfilename == null) {
                return;
            }
            ClipboardHolder cliph = null;
            Clipboard clip = null;
            try {
                cliph = sess.getClipboard();
            } catch (EmptyClipboardException e) {
            	actor.printError("Schematic is empty");
                return;
            }
            AffineTransform trans = new AffineTransform();
            // Get rotation for clipboard
            Rotation rot = def.getRotation();
            if (rot != Rotation.ROT0) {
                trans = rotate2D(trans, rot);
            }
            // Get flip option
            Flip flip = def.getFlip();
            switch (flip) {
                case NS:
                    trans = flip(trans, Direction.NORTH);
                    break;
                case EW:
                    trans = flip(trans, Direction.WEST);
                    break;
                default:
                    break;
            }
            cliph.setTransform(trans);

            clip = cliph.getClipboard();
            Region region = clip.getRegion();
            BlockVector3 clipOrigin = clip.getOrigin();
            Vector3 centerOffset = region.getCenter().subtract(clipOrigin.toVector3());

            // And apply clipboard to edit session
            Vector3 ppos;
            if (place == Placement.DROP) {
                ppos = Vector3.at(centerOffset.getX(), -def.offset - yoff - minY[0] + 1, centerOffset.getZ());
            }
            else if (place == Placement.BOTTOM) {
                ppos = Vector3.at(centerOffset.getX(), -def.offset -yoff + 1, centerOffset.getZ());
            }
            else { // Else, default is CENTER (same as clipboard brush
                ppos = Vector3.at(centerOffset.getX(), centerOffset.getY() - def.offset - yoff + 1, centerOffset.getZ());
            }
            ppos = trans.apply(ppos);
            ppos = pos.toVector3().subtract(ppos);

            if (!replaceall) {
                editsession.setMask(new BlockMask(editsession, List.of(BlockTypes.AIR.getDefaultState().toBaseBlock())));
            }
            PasteBuilder pb = cliph.createPaste(editsession.getWorld()).to(ppos.toBlockPoint())
                    .ignoreAirBlocks(skipair);
            Operations.completeLegacy(pb.build());
            actor.print("Applied '" + schfilename + "', flip=" + flip.name() + ", rot=" + rot.deg + ", place=" + place.name());
        }
    }
    
    private AffineTransform rotate2D(AffineTransform trans, Rotation rot) {
        return trans.rotateY(rot.ordinal() * 90);
    }
    
    private AffineTransform flip(AffineTransform trans, Direction dir) {
    	Vector3 d = dir.toVector();
    	Vector3 pos = Vector3.at(Math.abs(d.getX()), Math.abs(d.getY()), Math.abs(d.getZ()));
        return trans.scale(pos.multiply(-2).add(1,1,1));
    }

    private AffineTransform doOffset(AffineTransform trans, Vector3 off) {
        return trans.translate(off.multiply(-1));
    }

    private File getDirectoryForFormat(String fmt) {
        if (fmt.equals("schematic")) {  // Get from worldedit directory
            return new File(wep.getWorkingDir().toFile(), wep.getPlatform().getConfiguration().saveDir);
        }
        else {  // Else, our own type specific directory
            return new File(this.configDir, fmt);
        }
    }

 // Schematic tree cache - used during initialization
    private Map<File, List<String>> treecache = new HashMap<File, List<String>>();
    
    private void buildTree(File dir, List<String> rslt, String path) {
    	File[] lst = dir.listFiles();
    	for (File f : lst) {
            String n = (path == null) ? f.getName() : (path + "/" + f.getName());
    		if (f.isDirectory()) {
    			buildTree(f, rslt, n);
    		}
    		else {
    			rslt.add(n);
    		}
    	}
    }
    
    private List<String> getMatchingFiles(File dir, Pattern p) {
        ArrayList<String> matches = new ArrayList<String>();
        getMatchingFiles(matches, dir, p, null);
        return matches;
    }
    
    private void getMatchingFiles(List<String> rslt, File dir, final Pattern p, final String path) {
    	List<String> flist = null;
    	if (treecache != null) {
    		flist = treecache.get(dir);	// See if cached
    	}
    	if (flist == null) {
    		flist = new ArrayList<String>();
    		buildTree(dir, flist, null);
    		if (treecache != null) {
    			treecache.put(dir, flist);
    		}
    	}
    	for (String fn : flist) {
    		if (p.matcher(fn).matches()) {
                rslt.add(fn);
            }
        }
    }
    
    /* Resolve name to loadable name - if contains wildcards, pic random matching file */
    private String resolveName(Actor player, File dir, String fname, final String ext) {
        // If command-line style wildcards
        if ((!fname.startsWith("^")) && ((fname.indexOf('*') >= 0) || (fname.indexOf('?') >= 0))) {
            // Compile to regex
            fname = "^" + fname.replace(".","\\.").replace("*",  ".*").replace("?", ".");
        }
        if (fname.startsWith("^")) { // If marked as regex
            final int extlen = ext.length();
            try {
                final Pattern p = Pattern.compile(fname + "\\." + ext);
                List<String> files = getMatchingFiles(dir, p);
                if (files.isEmpty() == false) {    // Multiple choices?
                    String n = files.get(rnd.nextInt(files.size()));
                    n = n.substring(0, n.length() - extlen - 1);
                    return n;
                }
                else {
                    return null;
                }
            } catch (PatternSyntaxException x) {
                player.printError("Invalid filename pattern - " + fname + " - " + x.getMessage());
                return null;
            }
        }
        return fname;
    }
    
    private String loadSchematicIntoClipboard(Actor player, LocalSession sess, String fname, String format, int[] bottomY) {
        File dir = getDirectoryForFormat(format);
        if (dir == null) {
            player.printError("Schematic '" + fname + "' invalid format - " + format);
            return null;
        }
        String name = resolveName(player, dir, fname, format);
        if (name == null) {
            player.printError("Schematic '" + fname + "' file not found");
            return null;
        }
        File f;
        boolean rslt = false;
        Closer closer = Closer.create();
        try {
        	wep.
            f = we.getSafeOpenFile(null, dir, name, format);
            if (!f.exists()) {
                player.printError("Schematic '" + name + "' file not found");
                return null;
            }
            // Figure out format to use
            if (format.equals("schematic")) {
                ClipboardFormat fmt = ClipboardFormat.findByFile(f);

                if (fmt == null) {
                    player.printError("Schematic '" + name + "' format not found");
                    return null;
                }
                if (!fmt.isFormat(f)) {
                    player.printError("Schematic '" + name + "' is not correct format (" + fmt + ")");
                    return null;
                }
                String filePath = f.getCanonicalPath();
                String dirPath = dir.getCanonicalPath();

                if (!filePath.substring(0, dirPath.length()).equals(dirPath)) {
                    return null;
                } else {
                    FileInputStream fis = closer.register(new FileInputStream(f));
                    BufferedInputStream bis = closer.register(new BufferedInputStream(fis));
                    ClipboardReader reader = fmt.getReader(bis);

                    WorldData worldData = player.getWorld().getWorldData();
                    Clipboard cc = reader.read(player.getWorld().getWorldData());
                    if (cc != null) {
                        Region reg = cc.getRegion();
                        int minY = reg.getHeight() - 1;
                        for (int y = 0; (minY == -1) && (y < reg.getHeight()); y++) {
                            for (int x = 0; (minY == -1) && (x < reg.getWidth()); x++) {
                                for (int z = 0; (minY == -1) && (z < reg.getLength()); z++) {
                                    if (cc.getBlock(new Vector(x, y, z)) != null) {
                                        minY = y;
                                        break;
                                    }
                                }
                            }
                        }
                        bottomY[0] = minY;
                        sess.setClipboard(new ClipboardHolder(cc, worldData));
                        rslt = true;
                    }
                }
            }
            // Else if BO2 file
            else if (format.equals("bo2")) {
                Clipboard cc = loadBOD2File(f);
                if (cc != null) {
                    WorldData worldData = player.getWorld().getWorldData(;
                    sess.setClipboard(new ClipboardHolder(cc, worldData));
                    rslt = true;
                    bottomY[0] = 0; // Always zero for these: we compact things to bottom
                }
            }
            else {
                return null;
            }
        } catch (FilenameException e1) {
            player.printError(e1.getMessage());
        } catch (IOException e) {
            player.printError("Error reading schematic '" + name + "' - " + e.getMessage());
        } finally {
            try {
                closer.close();
            } catch (IOException ignored) {
            }
        }

        return (rslt)?name:null;
    }
    
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
