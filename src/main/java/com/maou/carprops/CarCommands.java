package com.maou.carprops;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * /carprop spawn [model] [skala]
 * /carprop scale|yaw|pitch|roll|steer <angka>   /carprop model <id>   /carprop color <nama|hex|default>
 * /carprop finish <gloss|metallic|matte|chrome|default>   /carprop lights <true|false>
 * /carprop remove   /carprop clear [radius]   /carprop info
 * Yang diubah = mobil yang sedang kamu lihat; kalau tidak ada, mobil terdekat (maks 32 blok).
 */
public final class CarCommands {
    private static final Map<String, Integer> COLORS = Map.ofEntries(
            Map.entry("pink", 0xE0589C), Map.entry("hotpink", 0xFF2D95), Map.entry("black", 0x101010),
            Map.entry("white", 0xF2F2F2), Map.entry("red", 0xD01818), Map.entry("blue", 0x1F4FD8),
            Map.entry("green", 0x1FA84A), Map.entry("yellow", 0xF2C81A), Map.entry("orange", 0xF07818),
            Map.entry("purple", 0x7A2FD0), Map.entry("silver", 0xB8BCC4), Map.entry("gray", 0x6C6F75),
            Map.entry("gold", 0xD4A62A), Map.entry("cyan", 0x18C8D8));
    private static final List<String> FINISHES = List.of("gloss", "metallic", "matte", "chrome");

    private CarCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("carprop").requires(s -> s.hasPermission(2));

        root.then(Commands.literal("spawn")
                .executes(c -> spawn(c.getSource(), null, -1f))
                .then(Commands.argument("model", StringArgumentType.word())
                        .executes(c -> spawn(c.getSource(), StringArgumentType.getString(c, "model"), -1f))
                        .then(Commands.argument("scale", FloatArgumentType.floatArg(0.05f, 30f))
                                .executes(c -> spawn(c.getSource(), StringArgumentType.getString(c, "model"),
                                        FloatArgumentType.getFloat(c, "scale"))))));

        root.then(floatCmd("scale", 0.05f, 30f, (car) -> {}, (car, v) -> car.setCarScale(v)));
        root.then(floatCmd("yaw", -360f, 360f, (car) -> {}, (car, v) -> car.setYRot(v)));
        root.then(floatCmd("pitch", -180f, 180f, (car) -> {}, (car, v) -> car.setPitchTilt(v)));
        root.then(floatCmd("roll", -180f, 180f, (car) -> {}, (car, v) -> car.setRollTilt(v)));
        root.then(floatCmd("steer", -45f, 45f, (car) -> {}, (car, v) -> car.setSteer(v)));

        root.then(Commands.literal("model").then(Commands.argument("id", StringArgumentType.word())
                .executes(c -> {
                    String id = StringArgumentType.getString(c, "id");
                    return with(c.getSource(), car -> car.setModelId(id), "Model = " + id);
                })));

        root.then(Commands.literal("color").then(Commands.argument("value", StringArgumentType.word())
                .executes(c -> {
                    String v = StringArgumentType.getString(c, "value").toLowerCase(Locale.ROOT);
                    int rgb;
                    if (v.equals("default")) rgb = -1;
                    else if (COLORS.containsKey(v)) rgb = COLORS.get(v);
                    else {
                        try { rgb = Integer.parseInt(v.startsWith("#") ? v.substring(1) : v, 16) & 0xFFFFFF; }
                        catch (NumberFormatException e) {
                            c.getSource().sendFailure(Component.literal("Warna: default, " + String.join(", ", COLORS.keySet()) + " atau hex 6 digit (contoh ff66aa)"));
                            return 0;
                        }
                    }
                    final int fin = rgb;
                    return with(c.getSource(), car -> car.setColor(fin), "Warna = " + v);
                })));

        root.then(Commands.literal("finish").then(Commands.argument("type", StringArgumentType.word())
                .executes(c -> {
                    String f = StringArgumentType.getString(c, "type").toLowerCase(Locale.ROOT);
                    if (!f.equals("default") && !FINISHES.contains(f)) {
                        c.getSource().sendFailure(Component.literal("Finish: default, gloss, metallic, matte, chrome"));
                        return 0;
                    }
                    return with(c.getSource(), car -> car.setFinish(f.equals("default") ? "" : f), "Finish = " + f);
                })));

        root.then(Commands.literal("lights").then(Commands.argument("on", BoolArgumentType.bool())
                .executes(c -> {
                    boolean on = BoolArgumentType.getBool(c, "on");
                    return with(c.getSource(), car -> car.setLightsOn(on), "Lampu " + (on ? "nyala" : "mati"));
                })));

        root.then(Commands.literal("remove").executes(c -> {
            CarPropEntity car = target(c.getSource());
            if (car == null) { c.getSource().sendFailure(Component.literal("Tidak ada mobil di dekatmu.")); return 0; }
            car.discard();
            c.getSource().sendSuccess(() -> Component.literal("Mobil dihapus."), false);
            return 1;
        }));

        root.then(Commands.literal("clear")
                .executes(c -> clear(c.getSource(), 64f))
                .then(Commands.argument("radius", FloatArgumentType.floatArg(1f, 512f))
                        .executes(c -> clear(c.getSource(), FloatArgumentType.getFloat(c, "radius")))));

        root.then(Commands.literal("info").executes(c -> {
            CarPropEntity car = target(c.getSource());
            if (car == null) { c.getSource().sendFailure(Component.literal("Tidak ada mobil di dekatmu.")); return 0; }
            c.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "model=%s skala=%.2f yaw=%.0f pitch=%.0f roll=%.0f steer=%.0f warna=%s finish=%s lampu=%s",
                    car.getModelId(), car.getCarScale(), car.getYRot(), car.getPitchTilt(), car.getRollTilt(), car.getSteer(),
                    car.getColor() < 0 ? "default" : String.format("#%06X", car.getColor()),
                    car.getFinish().isEmpty() ? "default" : car.getFinish(), car.isLightsOn())), false);
            return 1;
        }));

        d.register(root);
    }

    // ---------- helper ----------
    private interface FloatSetter { void set(CarPropEntity car, float v); }

    private static LiteralArgumentBuilder<CommandSourceStack> floatCmd(String name, float min, float max,
                                                                       Consumer<CarPropEntity> unused, FloatSetter setter) {
        return Commands.literal(name).then(Commands.argument("value", FloatArgumentType.floatArg(min, max))
                .executes(c -> {
                    float v = FloatArgumentType.getFloat(c, "value");
                    return with(c.getSource(), car -> setter.set(car, v), name + " = " + v);
                }));
    }

    private static int with(CommandSourceStack src, Consumer<CarPropEntity> action, String msg) {
        CarPropEntity car = target(src);
        if (car == null) {
            src.sendFailure(Component.literal("Tidak ada mobil di dekatmu (maks 32 blok). Taruh dulu: /carprop spawn"));
            return 0;
        }
        action.accept(car);
        src.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int spawn(CommandSourceStack src, String model, float scale) {
        ServerLevel level = src.getLevel();
        Vec3 pos = src.getPosition();
        float yaw = 0f;
        try {
            ServerPlayer p = src.getPlayerOrException();
            yaw = p.getYRot() + 180.0f; // depan mobil menghadap kamu
        } catch (CommandSyntaxException ignored) { }
        CarPropEntity car = new CarPropEntity(CarProps.CAR_ENTITY, level);
        car.moveTo(pos.x, pos.y, pos.z, yaw, 0f);
        car.setModelId(model != null ? model : CarPropsConfig.get().defaultModel);
        car.setCarScale(scale > 0 ? scale : (float) CarPropsConfig.get().defaultScale);
        level.addFreshEntity(car);
        src.sendSuccess(() -> Component.literal("Mobil ditaruh: " + car.getModelId()), false);
        return 1;
    }

    private static int clear(CommandSourceStack src, float radius) {
        Vec3 pos = src.getPosition();
        List<CarPropEntity> list = src.getLevel().getEntitiesOfClass(CarPropEntity.class,
                new AABB(pos, pos).inflate(radius));
        list.forEach(CarPropEntity::discard);
        src.sendSuccess(() -> Component.literal(list.size() + " mobil dihapus."), false);
        return list.size();
    }

    /** Mobil yang sedang dilihat pemain; kalau tidak ada, yang terdekat. */
    private static CarPropEntity target(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        Vec3 pos = src.getPosition();
        List<CarPropEntity> near = level.getEntitiesOfClass(CarPropEntity.class, new AABB(pos, pos).inflate(32));
        if (near.isEmpty()) return null;

        if (src.getEntity() != null) {
            Vec3 eye = src.getEntity() instanceof net.minecraft.world.entity.player.Player pl ? pl.getEyePosition() : pos;
            Vec3 end = eye.add(src.getEntity().getViewVector(1.0f).scale(48.0));
            CarPropEntity best = null;
            double bestD = Double.MAX_VALUE;
            for (CarPropEntity car : near) {
                Optional<Vec3> hit = car.getBoundingBoxForCulling().clip(eye, end);
                if (hit.isPresent()) {
                    double dd = hit.get().distanceToSqr(eye);
                    if (dd < bestD) { bestD = dd; best = car; }
                }
            }
            if (best != null) return best;
        }
        CarPropEntity nearest = null;
        double nd = Double.MAX_VALUE;
        for (CarPropEntity car : near) {
            double dd = car.distanceToSqr(pos);
            if (dd < nd) { nd = dd; nearest = car; }
        }
        return nearest;
    }
}
