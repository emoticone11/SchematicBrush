package com.westeroscraft.schematicbrush.commands;

import com.westeroscraft.schematicbrush.SchematicBrush;
import com.westeroscraft.schematicbrush.SchematicDef;
import com.westeroscraft.schematicbrush.SchematicSet;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import org.enginehub.piston.exception.StopExecutionException;

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
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;
import com.sk89q.worldedit.util.io.Closer;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.regions.selector.limit.PermissiveSelectorLimits;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.Vec3;


public class SCHSETCommand {
	private static SchematicBrush sb;
	
	public static void register(SchematicBrush mod, CommandDispatcher<CommandSourceStack> source) {
		sb = mod;
		SchematicBrush.log.info("Register schset");
    source.register(Commands.literal("schset")
      .then(Commands.literal("list")
        .executes(ctx -> schSetList(null, ctx.getSource()))
        .then(Commands.argument("contains", StringArgumentType.word())
          .executes(ctx -> schSetList(StringArgumentType.getString(ctx, "contains"), ctx.getSource()))))
      .then(Commands.literal("create")
        .then(Commands.argument("setid", StringArgumentType.word())
          .executes(ctx -> schSetCreate(StringArgumentType.getString(ctx, "setid"), null, ctx.getSource()))
          .then(Commands.argument("schems", StringArgumentType.greedyString())
            .executes(ctx -> schSetCreate(StringArgumentType.getString(ctx, "setid"),
                                          StringArgumentType.getString(ctx, "schems"), ctx.getSource())))))
      .then(Commands.literal("delete")
        .then(Commands.argument("setid", StringArgumentType.word())
          .executes(ctx -> schSetDelete(StringArgumentType.getString(ctx, "setid"), ctx.getSource()))))
      .then(Commands.literal("append")
        .then(Commands.argument("setid", StringArgumentType.word())
          .then(Commands.argument("schems", StringArgumentType.greedyString())
            .executes(ctx -> schSetAppend(StringArgumentType.getString(ctx, "setid"),
                                          StringArgumentType.getString(ctx, "schems"), ctx.getSource())))))
      .then(Commands.literal("remove")
        .then(Commands.argument("setid", StringArgumentType.word())
          .then(Commands.argument("schems", StringArgumentType.greedyString())
            .executes(ctx -> schSetRemove(StringArgumentType.getString(ctx, "setid"),
                                          StringArgumentType.getString(ctx, "schems"), ctx.getSource())))))
      .then(Commands.literal("setdesc")
        .then(Commands.argument("setid", StringArgumentType.word())
          .then(Commands.argument("desc", StringArgumentType.string())
            .executes(ctx -> schSetSetDesc(StringArgumentType.getString(ctx, "setid"),
                                           StringArgumentType.getString(ctx, "desc"), ctx.getSource())))))
      .then(Commands.literal("get")
        .then(Commands.argument("setid", StringArgumentType.word())
          .executes(ctx -> schSetGet(StringArgumentType.getString(ctx, "setid"), ctx.getSource())))));
	}

  /*
   * List all schemsets.
   */
  public static int schSetList(String contains, CommandSourceStack source) {
    Actor actor = sb.validateActor(source, "schematicbrush.set.list");
    if (actor != null) {

      int cnt = 0;
      TreeSet<String> keys = new TreeSet<String>(sb.sets.keySet());
      for (String k : keys) {
        if ((contains != null) && (!k.contains(contains))) {
          continue;
        }
        SchematicSet ss = sb.sets.get(k);
        actor.printInfo(TextComponent.of(ss.name + ": desc='" + ss.desc + "'"));
        cnt++;
      }
      actor.printInfo(TextComponent.of(cnt + " sets returned"));
    }

    return 1;
  }

  /*
   * Create a new schemset, optionally initialized with a list of schematic definitions.
   */
  public static int schSetCreate(String setid, String schemsStr, CommandSourceStack source) {
    Actor actor = sb.validateActor(source, "schematicbrush.set.create");
    if (actor != null) {

      // Existing ID?
      if (sb.sets.containsKey(setid)) {
        actor.printInfo(TextComponent.of("Set '" + setid + "' already defined"));
        return 1;
      }

      SchematicSet ss = new SchematicSet(setid, "", null);
      sb.addSchematicSet(ss);

      // Any other arguments are schematic IDs to add
      if (schemsStr != null) {
        String[] schemids = schemsStr.split(" ");
        for (String schemid : schemids) {
          SchematicDef def = SchematicDef.parseSchematic(schemid);
          if ((def != null) && sb.validateSchematicDef(actor, def)) {
            ss.schematics.add(def);
          } else {
            actor.printInfo(TextComponent.of("Schematic '" + schemid + "' invalid - ignored"));
          }
        }
      }

      sb.saveSchematicSets();
      actor.printInfo(TextComponent.of("Set '" + setid + "' created"));
    }

    return 1;
  }

  /*
   * Delete a schemset.
   */
  public static int schSetDelete(String setid, CommandSourceStack source) {
    Actor actor = sb.validateActor(source, "schematicbrush.set.delete");
    if (actor != null) {

      // Existing ID?
      if (!sb.sets.containsKey(setid)) {
        actor.printInfo(TextComponent.of("Set '" + setid + "' not defined"));
        return 1;
      }

      SchematicSet set = sb.sets.get(setid);
      sb.removeSchematicSet(set);
  
      sb.saveSchematicSets();
      actor.printInfo(TextComponent.of("Set '" + setid + "' deleted"));
    }

    return 1;
  }

  /*
   * Append a list of schematic definitions to a schemset.
   */
  public static int schSetAppend(String setid, String schemsStr, CommandSourceStack source) {
    Actor actor = sb.validateActor(source, "schematicbrush.set.append");
    if (actor != null) {

      // Existing ID?
      if (!sb.sets.containsKey(setid)) {
        actor.printInfo(TextComponent.of("Set '" + setid + "' not defined"));
        return 1;
      }

      SchematicSet ss = sb.sets.get(setid);

      // Any other arguments are schematic IDs to add
      if (schemsStr != null) {
        String[] schemids = schemsStr.split(" ");
        for (String schemid : schemids) {
          SchematicDef def = SchematicDef.parseSchematic(schemid);
          if ((def != null) && sb.validateSchematicDef(actor, def)) {
            ss.schematics.add(def);
          } else {
            actor.printInfo(TextComponent.of("Schematic '" + schemid + "' invalid - ignored"));
          }
        }
      }

      sb.saveSchematicSets();
      actor.printInfo(TextComponent.of("Set '" + setid + "' updated"));
    }

    return 1;
  }

  /*
   * Remove a list of schematic names from a schemset.
   */
  public static int schSetRemove(String setid, String schemsStr, CommandSourceStack source) {
    Actor actor = sb.validateActor(source, "schematicbrush.set.remove");
    if (actor != null) {


    }

    // TODO: split schemsStr by space
    return 1;
  }

  /*
   * Set the description of a schemset.
   */
  public static int schSetSetDesc(String setid, String desc, CommandSourceStack source) {
    Actor actor = sb.validateActor(source, "schematicbrush.set.setdesc");
    if (actor != null) {


    }

    return 1;
  }

  /*
   * Get the schematics within a schemset.
   */
  public static int schSetGet(String setid, CommandSourceStack source) {
    Actor actor = sb.validateActor(source, "schematicbrush.set.get");
    if (actor != null) {


    }

    return 1;
  }
}
