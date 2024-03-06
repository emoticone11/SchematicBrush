package com.westeroscraft.schematicbrush.commands;

import com.westeroscraft.schematicbrush.SchematicBrush;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import org.enginehub.piston.exception.StopExecutionException;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalConfiguration;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extension.platform.permission.ActorSelectorLimits;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;
import com.sk89q.worldedit.util.io.Closer;
import com.sk89q.worldedit.util.Direction;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.command.tool.BrushTool;
import com.sk89q.worldedit.command.tool.InvalidToolBindException;
import com.sk89q.worldedit.command.tool.brush.Brush;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.math.transform.AffineTransform;
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


public class SCHBRCommand {
	private static SchematicBrush sb;
	
	public static void register(SchematicBrush mod, CommandDispatcher<CommandSourceStack> source) {
		sb = mod;
		SchematicBrush.log.info("Register schbr");
    // This is messy because brigadier doesn't appear to have a good system for optional flags...
    source.register(Commands.literal("schbr")
      .then(Commands.argument("args", StringArgumentType.greedyString())
        .executes(ctx -> schBr(false, false, 0,
                               StringArgumentType.getString(ctx, "args"), ctx.getSource())))
      .then(Commands.literal("-incair")
        .then(Commands.argument("args", StringArgumentType.greedyString())
          .executes(ctx -> schBr(true, false, 0,
                                 StringArgumentType.getString(ctx, "args"), ctx.getSource())))
        .then(Commands.literal("-replaceall")
          .then(Commands.argument("args", StringArgumentType.greedyString())
            .executes(ctx -> schBr(true, true, 0,
                                   StringArgumentType.getString(ctx, "args"), ctx.getSource())))
          .then(Commands.literal("-yoff")
            .then(Commands.argument("yoff", IntegerArgumentType.integer())
              .then(Commands.argument("args", StringArgumentType.greedyString())
                .executes(ctx -> schBr(true, true, IntegerArgumentType.getInteger(ctx, "yoff"),
                                       StringArgumentType.getString(ctx, "args"), ctx.getSource()))))))
        .then(Commands.literal("-yoff")
          .then(Commands.argument("yoff", IntegerArgumentType.integer())
            .then(Commands.argument("args", StringArgumentType.greedyString())
              .executes(ctx -> schBr(true, false, IntegerArgumentType.getInteger(ctx, "yoff"),
                                     StringArgumentType.getString(ctx, "args"), ctx.getSource()))))))
      .then(Commands.literal("-replaceall")
        .then(Commands.argument("args", StringArgumentType.greedyString())
          .executes(ctx -> schBr(false, true, 0,
                                 StringArgumentType.getString(ctx, "args"), ctx.getSource())))
        .then(Commands.literal("-yoff")
          .then(Commands.argument("yoff", IntegerArgumentType.integer())
            .then(Commands.argument("args", StringArgumentType.greedyString())
              .executes(ctx -> schBr(false, true, IntegerArgumentType.getInteger(ctx, "yoff"),
                                     StringArgumentType.getString(ctx, "args"), ctx.getSource()))))))
      .then(Commands.literal("-yoff")
        .then(Commands.argument("yoff", IntegerArgumentType.integer())
          .then(Commands.argument("args", StringArgumentType.greedyString())
            .executes(ctx -> schBr(false, false, IntegerArgumentType.getInteger(ctx, "yoff"),
                                   StringArgumentType.getString(ctx, "args"), ctx.getSource()))))));
	}

  /*
   * Apply the schembrush to a WorldEdit brush.
   */
	public static int schBr(boolean incair, boolean replaceall, int yoff, String args, CommandSourceStack source) {
    Actor actor = sb.validateActor(source, "schematicbrush.brush.use");
    if (actor != null) {

      // TODO
    }

		return 1;
	}
}
