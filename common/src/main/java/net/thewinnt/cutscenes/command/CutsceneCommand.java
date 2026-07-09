package net.thewinnt.cutscenes.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.thewinnt.cutscenes.CutsceneManager;
import net.thewinnt.cutscenes.CutsceneType;
import net.thewinnt.cutscenes.event.EndingReason;
import net.thewinnt.cutscenes.util.ServerPlayerExt;

import java.util.Collection;
import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class CutsceneCommand {
    public static final DynamicCommandExceptionType PLAYER_ALREADY_IN_CUTSCENE = new DynamicCommandExceptionType(obj -> Component.translatable("commands.cutscene.error.player_already_in_cutscene", obj));
    public static final DynamicCommandExceptionType PLAYER_NOT_IN_CUTSCENE = new DynamicCommandExceptionType(obj -> Component.translatable("commands.cutscene.error.player_not_in_cutscene", obj));
    public static final SimpleCommandExceptionType MISSING_RUNNER = new SimpleCommandExceptionType(Component.translatable("commands.cutscene.error.no_runner"));
    public static final SimpleCommandExceptionType NO_PREVIEW = new SimpleCommandExceptionType(Component.translatable("commands.cutscene.error.no_preview"));
    public static final DynamicCommandExceptionType NO_CUTSCENE = new DynamicCommandExceptionType(obj -> Component.translatable("commands.cutscene.error.no_such_cutscene", obj));
    public static final SuggestionProvider<CommandSourceStack> SUGGEST_CUTSCENES = (stack, builder) -> SharedSuggestionProvider.suggestResource(CutsceneManager.REGISTRY.keySet(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("cutscene")
                .requires(source -> source.hasPermission(2))
                .then(startCommand())
                .then(stopCommand())
                .then(previewCommand())
                .then(getCommand())
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> startCommand() {
        return literal("start")
                .then(argument("players", EntityArgument.players())
                .then(argument("type", ResourceLocationArgument.id()).suggests(SUGGEST_CUTSCENES)
                .executes(context -> showCutscene(
                        context.getSource(),
                        type(context),
                        players(context),
                        context.getSource().getPosition(),
                        Vec3.ZERO,
                        Vec3.ZERO,
                        "command"
                ))
                .then(literal("at_preview")
                        .executes(context -> showAtPreview(context, Vec3.ZERO, "command"))
                        .then(argument("camera_rotation_xy", RotationArgument.rotation())
                        .then(argument("camera_rotation_z", DoubleArgumentType.doubleArg())
                        .executes(context -> showAtPreview(context, cameraRotation(context), "command"))
                        .then(argument("reason", StringArgumentType.string())
                        .executes(context -> showAtPreview(context, cameraRotation(context), reason(context))))))
                        .then(argument("reason", StringArgumentType.string())
                        .executes(context -> showAtPreview(context, Vec3.ZERO, reason(context)))))
                .then(argument("reason", StringArgumentType.string())
                        .executes(context -> showCutscene(
                                context.getSource(),
                                type(context),
                                players(context),
                                context.getSource().getPosition(),
                                Vec3.ZERO,
                                Vec3.ZERO,
                                reason(context)
                        )))
                .then(argument("start_pos", Vec3Argument.vec3())
                        .executes(context -> showFromStartPos(context, Vec3.ZERO, Vec3.ZERO, "command"))
                        .then(argument("camera_rotation_xy", RotationArgument.rotation())
                        .then(argument("camera_rotation_z", DoubleArgumentType.doubleArg())
                        .executes(context -> showFromStartPos(context, cameraRotation(context), Vec3.ZERO, "command"))
                        .then(argument("path_rotation_xy", RotationArgument.rotation())
                        .then(argument("path_rotation_z", DoubleArgumentType.doubleArg())
                        .executes(context -> showFromStartPos(context, cameraRotation(context), pathRotation(context), "command"))
                        .then(argument("reason", StringArgumentType.string())
                        .executes(context -> showFromStartPos(context, cameraRotation(context), pathRotation(context), reason(context))))))
                        .then(argument("reason", StringArgumentType.string())
                        .executes(context -> showFromStartPos(context, cameraRotation(context), Vec3.ZERO, reason(context))))))
                        .then(argument("reason", StringArgumentType.string())
                        .executes(context -> showFromStartPos(context, Vec3.ZERO, Vec3.ZERO, reason(context)))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> stopCommand() {
        return literal("stop")
                .executes(context -> stopCutscenes(context.getSource(), List.of(context.getSource().getPlayerOrException())))
                .then(argument("players", EntityArgument.players())
                .executes(context -> stopCutscenes(context.getSource(), players(context))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> previewCommand() {
        return literal("preview")
                .then(literal("set")
                .then(argument("cutscene", ResourceLocationArgument.id()).suggests(SUGGEST_CUTSCENES)
                .executes(context -> setPreview(context, context.getSource().getPosition(), 0.0F, 0.0F, 0.0F))
                .then(argument("start_pos", Vec3Argument.vec3())
                .executes(context -> setPreview(context, Vec3Argument.getVec3(context, "start_pos"), 0.0F, 0.0F, 0.0F))
                .then(argument("path_rotation_xy", RotationArgument.rotation())
                .then(argument("path_rotation_z", DoubleArgumentType.doubleArg())
                .executes(context -> {
                    Vec2 rotation = RotationArgument.getRotation(context, "path_rotation_xy").getRotation(context.getSource());
                    return setPreview(
                            context,
                            Vec3Argument.getVec3(context, "start_pos"),
                            rotation.x,
                            rotation.y,
                            (float) DoubleArgumentType.getDouble(context, "path_rotation_z")
                    );
                }))))))
                .then(literal("hide")
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    CutsceneManager.setPreviewedCutscene(null, Vec3.ZERO, 0.0F, 0.0F, 0.0F);
                    source.sendSuccess(() -> Component.translatable("commands.cutscene.preview.hide"), true);
                    return 1;
                }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> getCommand() {
        return literal("get")
                .then(argument("player", EntityArgument.player())
                .then(literal("start_reason")
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                    ServerPlayerExt ext = (ServerPlayerExt) player;
                    if (ext.csapi$isWatchingCutscene()) {
                        source.sendSuccess(() -> Component.translatable("commands.cutscene.get.start_reason", player.getName(), ext.csapi$getStartReason()), false);
                        return 1;
                    }
                    throw PLAYER_NOT_IN_CUTSCENE.create(player.getName());
                })));
    }

    private static int showAtPreview(CommandContext<CommandSourceStack> context, Vec3 cameraRotation, String startingReason) throws CommandSyntaxException {
        ResourceLocation id = type(context);
        if (CutsceneManager.getPreviewedCutscene() != null && !id.equals(CutsceneManager.REGISTRY.inverse().get(CutsceneManager.getPreviewedCutscene()))) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.cutscene.warning.cutscene_mismatch").withStyle(ChatFormatting.GOLD), false);
        }
        return showCutscene(
                context.getSource(),
                id,
                players(context),
                new Vec3(CutsceneManager.getOffset()),
                cameraRotation,
                new Vec3(CutsceneManager.previewPathYaw, CutsceneManager.previewPathPitch, CutsceneManager.previewPathRoll),
                startingReason
        );
    }

    private static int showFromStartPos(CommandContext<CommandSourceStack> context, Vec3 cameraRotation, Vec3 pathRotation, String startingReason) throws CommandSyntaxException {
        ResourceLocation id = type(context);
        if (CutsceneManager.getPreviewedCutscene() != null && !id.equals(CutsceneManager.REGISTRY.inverse().get(CutsceneManager.getPreviewedCutscene()))) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.cutscene.warning.cutscene_mismatch").withStyle(ChatFormatting.GOLD), false);
        }
        return showCutscene(
                context.getSource(),
                id,
                players(context),
                Vec3Argument.getVec3(context, "start_pos"),
                cameraRotation,
                pathRotation,
                startingReason
        );
    }

    private static int showCutscene(CommandSourceStack source, ResourceLocation id, Collection<ServerPlayer> players, Vec3 pos, Vec3 camRot, Vec3 pathRot, String startingReason) throws CommandSyntaxException {
        if (!CutsceneManager.REGISTRY.containsKey(id)) {
            throw NO_CUTSCENE.create(id.toString());
        }

        for (ServerPlayer player : players) {
            CutsceneManager.startCutscene(id, pos, camRot, pathRot, player, startingReason);
        }

        if (players.size() == 1) {
            ServerPlayer player = players.iterator().next();
            source.sendSuccess(() -> Component.translatable("commands.cutscene.showing", id.toString(), player.getDisplayName()), true);
        } else {
            source.sendSuccess(() -> Component.literal("Showing cutscene " + id + " to " + players.size() + " players"), true);
        }

        return players.size();
    }

    private static int stopCutscenes(CommandSourceStack source, Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            CutsceneManager.stopCutscene(player, EndingReason.COMMAND);
        }

        if (players.size() == 1) {
            ServerPlayer player = players.iterator().next();
            source.sendSuccess(() -> Component.translatable("commands.cutscene.stopped", player.getDisplayName()), true);
        } else {
            source.sendSuccess(() -> Component.literal("Stopped cutscenes for " + players.size() + " players"), true);
        }

        return players.size();
    }

    private static int setPreview(CommandContext<CommandSourceStack> context, Vec3 position, float pathYaw, float pathPitch, float pathRoll) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ResourceLocation id = ResourceLocationArgument.getId(context, "cutscene");
        CutsceneType type = CutsceneManager.REGISTRY.get(id);
        if (type == null) {
            throw NO_CUTSCENE.create(id.toString());
        }
        source.sendSuccess(() -> Component.translatable("commands.cutscene.preview.from_block", id.toString()), true);
        CutsceneManager.setPreviewedCutscene(type, position, pathYaw, pathPitch, pathRoll);
        return 1;
    }

    private static ResourceLocation type(CommandContext<CommandSourceStack> context) {
        return ResourceLocationArgument.getId(context, "type");
    }

    private static Collection<ServerPlayer> players(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return EntityArgument.getPlayers(context, "players");
    }

    private static String reason(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "reason");
    }

    private static Vec3 cameraRotation(CommandContext<CommandSourceStack> context) {
        Vec2 rotation = RotationArgument.getRotation(context, "camera_rotation_xy").getRotation(context.getSource());
        double roll = DoubleArgumentType.getDouble(context, "camera_rotation_z");
        return sanitizeRotation(rotation.y, rotation.x, roll);
    }

    private static Vec3 pathRotation(CommandContext<CommandSourceStack> context) {
        Vec2 rotation = RotationArgument.getRotation(context, "path_rotation_xy").getRotation(context.getSource());
        double roll = DoubleArgumentType.getDouble(context, "path_rotation_z");
        return new Vec3(rotation.x, rotation.y, roll);
    }

    private static Vec3 sanitizeRotation(double xRot, double yRot, double zRot) {
        double x = xRot < -180.0D || xRot > 180.0D ? Double.NaN : xRot;
        double y = yRot < -90.0D || yRot > 90.0D ? Double.NaN : yRot;
        double z = zRot < -180.0D || zRot > 180.0D ? Double.NaN : zRot;
        return new Vec3(x, y, z);
    }
}
