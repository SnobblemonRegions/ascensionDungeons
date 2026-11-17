package com.ascensiondungeons.mc;

import com.ascensiondungeons.AscensionDungeonsLog;
import com.ascensiondungeons.outbreak.CapsuleLook;
import com.ascensiondungeons.outbreak.PokemonFinds;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;

/**
 * A Pokémon found in a dive chest, carried as an item until it is safe: a capsule. It is ordinary dive loot (marked with the run,
 * lost to a knock-out, possibly taken by the toll) until it is banked or carried out. Then its finder uses it and the Pokémon
 * joins their party (or PC), graded by AscensionLib. Only the finder can release it.
 *
 * <p>Two looks. <b>Sealed</b>, while at risk: Cobblemon's ball for the tier with a custom model number, which the resource pack
 * draws as a lab tank of tier-coloured fluid with only a dark shape inside (our own art). <b>Clear</b>, once banked or carried
 * out: Cobblemon's {@code pokemon_model} item, which each player's own Cobblemon draws as the real Pokémon, lightly tinted. No
 * Pokémon art is shipped by this mod. Without Cobblemon the capsule stays a plain item with the same words.
 */
public final class Capsules {
    public static final String TAG = "ascension_capsule";

    /** The run test capsules are marked with ({@code /dungeon capsule}); banking it with that command clears them. */
    public static final UUID TEST_RUN = new UUID(0x7E57CA75L, 0x7E57CA75L);

    /** The Cobblemon side: releasing, naming and drawing Pokémon. Present only when Cobblemon is installed. */
    public interface Releaser {
        /** Creates the Pokémon, adds it to the party (or PC when full) and grades it. Returns its name, or null on failure. */
        String release(ServerPlayer player, String species, int level, String floor, int zoneTier);

        /** The species' name and types, or null when Cobblemon does not know it. */
        default Info info(String species) { return null; }

        /** Cobblemon's {@code pokemon_model} item showing this species, tinted (RGBA 0..1), or null. */
        default ItemStack model(String species, float[] tint) { return null; }
    }

    public record Info(String name, List<Type> types) {}

    public record Type(String name, int colour) {}

    /** Where a find came from, for the tooltip. {@code band} is null outside the Depths. */
    public record Origin(String dungeon, String band, String difficulty, int stage) {}

    public static volatile Releaser releaser;

    /** Capsules released since the server started, by id: a recap counts one already opened as kept. */
    static final java.util.Set<String> RELEASED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static boolean released(String id) { return RELEASED.contains(id); }

    private Capsules() {}

    /** A sealed capsule for a find. */
    public static ItemStack make(PokemonFinds.Find find, String floor, int zoneTier, ServerPlayer finder, Origin origin) {
        Releaser r = releaser;
        Info info = null;
        try { info = r == null ? null : r.info(find.species()); }
        catch (RuntimeException | LinkageError e) { AscensionDungeonsLog.LOG.warn("[capsule] could not look up {}", find.species(), e); }
        CompoundTag tag = new CompoundTag();
        tag.putString("species", find.species());
        tag.putString("name", info == null ? CapsuleLook.fallbackName(find.species()) : info.name());
        tag.putInt("level", find.level());
        tag.putString("tier", find.tier().name());
        tag.putString("floor", floor);
        tag.putInt("zoneTier", zoneTier);
        tag.putUUID("owner", finder.getUUID());
        tag.putString("ownerName", finder.getGameProfile().getName());
        tag.putUUID("id", UUID.randomUUID());                 // two finds of one species never merge into a stack
        if (origin.dungeon() != null) tag.putString("dungeon", origin.dungeon());
        if (origin.band() != null) tag.putString("band", origin.band());
        if (origin.difficulty() != null) tag.putString("difficulty", origin.difficulty());
        tag.putInt("stage", origin.stage());
        tag.putLong("day", java.time.LocalDate.now().toEpochDay());
        if (info != null) {
            ListTag types = new ListTag();
            for (Type t : info.types()) { CompoundTag c = new CompoundTag(); c.putString("name", t.name()); c.putInt("colour", t.colour()); types.add(c); }
            tag.put("types", types);
        }
        ItemStack stack = new ItemStack(ball(find.tier()));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.put(TAG, tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(CapsuleLook.modelId(find.tier())));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        stack.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, net.minecraft.util.Unit.INSTANCE);
        render(stack, tag, false);
        return stack;
    }

    /** As {@link #make(PokemonFinds.Find, String, int, ServerPlayer, Origin)}, found outside the Depths at stage 0. */
    public static ItemStack make(PokemonFinds.Find find, String floor, int zoneTier, ServerPlayer finder, String dungeon, String difficulty) {
        return make(find, floor, zoneTier, finder, new Origin(dungeon, null, difficulty, 0));
    }

    /**
     * The capsule once it is safe (banked or carried out): Cobblemon's model of the Pokémon with the same data and the words for
     * a safe capsule. Anything that is not a capsule comes back as it is.
     */
    public static ItemStack cleared(ItemStack stack) {
        CompoundTag c = read(stack);
        if (c == null) return stack;
        PokemonFinds.Tier tier = tier(c);
        ItemStack out = null;
        Releaser r = releaser;
        try { out = r == null ? null : r.model(c.getString("species"), CapsuleLook.tint(tier)); }
        catch (RuntimeException | LinkageError e) { AscensionDungeonsLog.LOG.warn("[capsule] could not make the model of {}", c.getString("species"), e); }
        if (out == null || out.isEmpty()) out = stack.copy();
        else {
            out.setCount(stack.getCount());
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data != null) out.set(DataComponents.CUSTOM_DATA, data);
            out.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, net.minecraft.util.Unit.INSTANCE);
        }
        render(out, c, true);
        return out;
    }

    /** Writes the name and tooltip from the capsule's data. */
    static void render(ItemStack stack, CompoundTag c, boolean safe) {
        PokemonFinds.Tier tier = tier(c);
        String name = c.contains("name") ? c.getString("name") : CapsuleLook.fallbackName(c.getString("species"));
        Style plain = Style.EMPTY.withItalic(false);
        MutableComponent title = coloured(name, tier, plain.withBold(CapsuleLook.bold(tier)))
                .append(Component.literal("  Lv. " + c.getInt("level")).withStyle(plain.withBold(false).withColor(ChatFormatting.GRAY)));
        stack.set(DataComponents.CUSTOM_NAME, title);

        List<Component> lore = new ArrayList<>();
        lore.add(coloured(CapsuleLook.stars(tier) + " " + tier.label(), tier, plain.withBold(true))
                .append(Component.literal(" · " + CapsuleLook.KIND).withStyle(plain.withBold(false).withColor(ChatFormatting.DARK_GRAY))));
        if (c.contains("types", Tag.TAG_LIST)) {
            ListTag types = c.getList("types", Tag.TAG_COMPOUND);
            MutableComponent line = Component.empty().withStyle(plain);
            for (int i = 0; i < types.size(); i++) {
                CompoundTag t = types.getCompound(i);
                if (i > 0) line.append(Component.literal(" · ").withStyle(plain.withColor(ChatFormatting.DARK_GRAY)));
                line.append(Component.literal(t.getString("name")).withStyle(plain.withColor(TextColor.fromRgb(t.getInt("colour")))));
            }
            if (!types.isEmpty()) lore.add(line);
        }
        lore.add(Component.empty());
        String floor = c.getString("floor");
        MutableComponent meter = Component.literal("Ascension  ").withStyle(plain.withColor(ChatFormatting.GRAY));
        for (CapsuleLook.Segment s : CapsuleLook.meter(floor)) meter.append(Component.literal(s.text()).withStyle(plain.withColor(TextColor.fromRgb(s.colour()))));
        meter.append(Component.literal("  " + CapsuleLook.floorText(floor)).withStyle(plain.withColor(TextColor.fromRgb(CapsuleLook.rarityColour(floor)))));
        lore.add(meter);
        lore.add(Component.literal("Graded when released.").withStyle(plain.withColor(ChatFormatting.DARK_GRAY)));
        List<String> found = CapsuleLook.found(c.contains("band") ? c.getString("band") : null, c.getString("dungeon"), c.getString("difficulty"), c.getInt("stage"));
        for (int i = 0; i < found.size(); i++) {
            if (i == 0) lore.add(Component.literal("Found  ").withStyle(plain.withColor(ChatFormatting.GRAY))
                    .append(Component.literal(found.get(0)).withStyle(plain.withColor(ChatFormatting.WHITE))));
            else lore.add(Component.literal("       " + found.get(i)).withStyle(plain.withColor(ChatFormatting.GRAY)));
        }
        lore.add(Component.empty());
        List<String> status = CapsuleLook.status(safe, name);
        for (int i = 0; i < status.size(); i++)
            lore.add(Component.literal(status.get(i)).withStyle(plain.withColor(safe ? ChatFormatting.GREEN : i == 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY)));
        lore.add(Component.literal(CapsuleLook.bound(c.getString("ownerName"), c.getLong("day"))).withStyle(plain.withColor(ChatFormatting.DARK_GRAY)));
        stack.set(DataComponents.LORE, new ItemLore(lore));
    }

    private static MutableComponent coloured(String text, PokemonFinds.Tier tier, Style style) {
        int[] colours = CapsuleLook.colours(tier, text);
        if (CapsuleLook.stops(tier).length == 1) return Component.literal(text).withStyle(style.withColor(TextColor.fromRgb(colours[0])));
        MutableComponent out = Component.empty().withStyle(style);
        int[] cps = text.codePoints().toArray();
        for (int i = 0; i < cps.length; i++) out.append(Component.literal(new String(Character.toChars(cps[i]))).withStyle(style.withColor(TextColor.fromRgb(colours[i]))));
        return out;
    }

    private static PokemonFinds.Tier tier(CompoundTag c) {
        try { return PokemonFinds.Tier.valueOf(c.getString("tier")); } catch (IllegalArgumentException e) { return PokemonFinds.Tier.STANDARD; }
    }

    /** The capsule's data, or null when the stack is not a capsule. */
    public static CompoundTag read(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(TAG)) return null;
        return data.copyTag().getCompound(TAG);
    }

    /**
     * A player used a capsule. Releases it when it is theirs and safe; otherwise says why not. Never lets the item act as what
     * it looks like (a Poké Ball is never thrown). Returns null when the stack is not a capsule.
     */
    public static InteractionResult use(ServerPlayer player, ItemStack stack) {
        CompoundTag c = read(stack);
        if (c == null) return null;
        if (!player.getUUID().equals(c.getUUID("owner"))) {
            player.displayClientMessage(Component.literal("This capsule is bound to " + c.getString("ownerName") + ". Only they can release it.").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        if (RunLoot.runOf(stack) != null) {
            player.displayClientMessage(Component.literal("Not yet: bank it at a checkpoint or carry it out through the portal first.").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.FAIL;
        }
        Releaser r = releaser;
        if (r == null) {
            player.displayClientMessage(Component.literal("Pokémon can't be released on this server (Cobblemon is not installed).").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        String name;
        try {
            name = r.release(player, c.getString("species"), c.getInt("level"), c.getString("floor"), Math.max(1, c.getInt("zoneTier")));
        } catch (RuntimeException | LinkageError e) {
            AscensionDungeonsLog.LOG.error("[capsule] could not release {} for {}", c.getString("species"), player.getGameProfile().getName(), e);
            name = null;
        }
        if (name == null) {
            player.displayClientMessage(Component.literal("The capsule would not open. It is still yours; try again, or tell a moderator.").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        RELEASED.add(c.getUUID("id").toString());
        stack.shrink(1);
        player.sendSystemMessage(Component.literal(name + " joined your team!").withStyle(ChatFormatting.GREEN));
        player.playNotifySound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.3f);
        AscensionDungeonsLog.LOG.info("[capsule] {} released {} (level {}, {}, floor {}, zone tier {})", player.getGameProfile().getName(), c.getString("species"),
                c.getInt("level"), c.getString("tier"), c.getString("floor"), c.getInt("zoneTier"));
        return InteractionResult.SUCCESS;
    }

    private static Item ball(PokemonFinds.Tier tier) {
        String id = switch (tier) {
            case STANDARD -> "cobblemon:poke_ball";
            case STARTER -> "cobblemon:great_ball";
            case PSEUDO -> "cobblemon:ultra_ball";
            case LEGENDARY -> "cobblemon:master_ball";
            case MYTHICAL -> "cobblemon:cherish_ball";
        };
        ResourceLocation rl = ResourceLocation.parse(id);
        return BuiltInRegistries.ITEM.containsKey(rl) ? BuiltInRegistries.ITEM.get(rl) : Items.ENDER_EYE;
    }

    /** "mrmime" -> "Mrmime": only for messages when Cobblemon could not name the species. */
    static String displayName(String species) { return CapsuleLook.fallbackName(species); }
}
