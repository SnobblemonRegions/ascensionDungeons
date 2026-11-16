package com.ascensiondungeons.mc;

import com.ascensiondungeons.AscensionDungeonsLog;
import com.ascensiondungeons.gen.authored.AuthoredDungeons;
import com.ascensiondungeons.gen.authored.AuthoredLayout;
import com.ascensiondungeons.outbreak.Onboarding;
import com.ascensiondungeons.outbreak.PartyBook;
import com.ascensiondungeons.outbreak.Readiness;
import com.ascensiondungeons.run.RunConfig;
import com.ascensiondungeons.run.RunManager;
import com.ascensiondungeons.run.StartResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * How a player gets into a dungeon: {@code /dive} opens a picker of the type dungeons, gentlest first, with what each asks
 * and whether this player (and their party) looks ready for it; a click starts the dive. A party is made by inviting, and
 * each guest accepts for themselves. The rules are {@link Readiness}'s and {@link PartyBook}'s; this class shows and applies them.
 */
public final class DiveEntry {
    /** A player's Pokémon team (Cobblemon). Present only when Cobblemon is installed. */
    public interface TeamInfo {
        /** The levels of the party Pokémon that can fight. */
        int[] levels(ServerPlayer player);
        /** How many party Pokémon have fainted. */
        int fainted(ServerPlayer player);
    }

    public static volatile TeamInfo teamInfo;
    /** Set while a server is running; null otherwise. */
    public static volatile DiveEntry current;

    private final MinecraftServer server;
    private final Supplier<RunManager> manager;
    private final RunConfig cfg;
    private final PartyBook book;
    private List<Readiness.Card> cards;

    public DiveEntry(MinecraftServer server, Supplier<RunManager> manager, RunConfig cfg) {
        this.server = server;
        this.manager = manager;
        this.cfg = cfg;
        this.book = new PartyBook(Math.max(1, cfg.maxPartySize), Math.max(1, cfg.partyInviteSeconds) * 20L);
    }

    /** Whether anyone may use /dive, or only operators. */
    public boolean openToAll() { return cfg.playerDives; }

    /** The dungeons on offer, gentlest first: every authored dungeon that is an Outbreak dive. */
    public synchronized List<Readiness.Card> cards() {
        if (cards == null) {
            List<Readiness.Card> found = new ArrayList<>();
            for (String id : AuthoredDungeons.ids()) {
                try {
                    AuthoredLayout layout = AuthoredLayout.get(id);
                    if (layout.outbreak() != null) found.add(layout.card());
                } catch (RuntimeException e) {
                    AscensionDungeonsLog.LOG.warn("[dive] dungeon {} cannot be offered", id, e);
                }
            }
            found.sort(Readiness::order);
            cards = List.copyOf(found);
        }
        return cards;
    }

    /** What this player brings: their team (when Cobblemon is installed) and the room in their pockets. */
    public Readiness.Team team(ServerPlayer p) {
        int free = 0;
        for (int i = 0; i < 36; i++) if (p.getInventory().getItem(i).isEmpty()) free++;
        TeamInfo info = teamInfo;
        if (info != null) {
            try { return new Readiness.Team(true, info.levels(p), info.fainted(p), free); }
            catch (RuntimeException | LinkageError e) { AscensionDungeonsLog.LOG.warn("[dive] could not read the team of {}", p.getGameProfile().getName(), e); }
        }
        return new Readiness.Team(false, new int[0], 0, free);
    }

    private boolean rookie(ServerPlayer p) { return !OnboardingStore.of(server).progress(p.getUUID()).graduated(); }

    /** The check at the door for one player and one dungeon. */
    public Readiness.Verdict verdict(ServerPlayer p, Readiness.Card card) {
        return Readiness.assess(card, team(p), rookie(p), Readiness.gentlest(cards()));
    }

    /** Everything worth saying before this party goes into this dungeon: the leader's own concerns, then each guest's by name. */
    public List<String> concerns(ServerPlayer leader, Readiness.Card card) {
        List<String> out = new ArrayList<>(verdict(leader, card).lines());
        for (UUID id : book.party(leader.getUUID())) {
            if (id.equals(leader.getUUID())) continue;
            ServerPlayer guest = server.getPlayerList().getPlayer(id);
            if (guest == null) { out.add("A player in your party is offline."); continue; }
            for (String line : verdict(guest, card).lines()) out.add(guest.getGameProfile().getName() + ": " + line);
        }
        return out;
    }

    // ---- the picker ----------------------------------------------------------------------------------------------

    public void open(ServerPlayer p) {
        if (manager.get() == null) { p.sendSystemMessage(Component.literal("Dungeons are not available right now.").withStyle(ChatFormatting.RED)); return; }
        if (manager.get().runOf(p.getUUID()).isPresent()) { p.sendSystemMessage(Component.literal("You are already in a dungeon run.").withStyle(ChatFormatting.RED)); return; }
        if (!book.leads(p.getUUID())) {
            ServerPlayer leader = server.getPlayerList().getPlayer(book.leader(p.getUUID()));
            p.sendSystemMessage(Component.literal("Your party's leader" + (leader == null ? "" : ", " + leader.getGameProfile().getName() + ",") + " chooses the dungeon. /dive leave to go your own way.")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        SimpleContainer box = new SimpleContainer(27);
        fill(p, box, -1);
        DiveMenu[] opened = new DiveMenu[1];
        p.openMenu(new SimpleMenuProvider((id, inv, player) -> opened[0] = new DiveMenu(id, inv, box, slot -> clicked(p, opened[0], box, slot)), Component.literal("Choose a dungeon")));
    }

    private void fill(ServerPlayer p, SimpleContainer box, int armed) {
        List<Readiness.Card> all = cards();
        Readiness.Card gentlest = Readiness.gentlest(all);
        boolean rookie = rookie(p);
        for (int i = 0; i < all.size() && i < 18; i++) box.setItem(i, dungeonItem(p, all.get(i), rookie && all.get(i) == gentlest, i == armed));
        Set<UUID> party = book.party(p.getUUID());
        List<String> lore = new ArrayList<>();
        for (UUID id : party) {
            ServerPlayer member = server.getPlayerList().getPlayer(id);
            lore.add((id.equals(p.getUUID()) ? "You" : member == null ? "(offline)" : member.getGameProfile().getName()) + (id.equals(book.leader(id)) ? " (leader)" : ""));
        }
        lore.add("");
        lore.add("/dive invite <player> to bring someone (up to " + book.maxSize() + ").");
        lore.add("They join with /dive accept.");
        box.setItem(DiveMenu.PARTY, item(Items.PLAYER_HEAD, party.size() == 1 ? "Diving alone" : "Your party: " + party.size() + " players", ChatFormatting.AQUA, lore, ChatFormatting.GRAY, false));
        box.setItem(DiveMenu.GUIDE, item(Items.BOOK, "Dive guide", ChatFormatting.GOLD, List.of("How a dive works, lesson by lesson.", "Click to open, or use /dungeon guide."), ChatFormatting.GRAY, false));
    }

    private ItemStack dungeonItem(ServerPlayer p, Readiness.Card card, boolean recommended, boolean armed) {
        List<String> concerns = concerns(p, card);
        List<Component> lore = new ArrayList<>();
        lore.add(line(card.typeName() + " type, tier " + card.tier(), ChatFormatting.GRAY));
        lore.add(line("Enemies: level " + card.minLevel() + " to " + card.maxLevel(), ChatFormatting.GRAY));
        lore.add(line("Gatekeeper: level " + card.keeperLevel() + " and up", ChatFormatting.GRAY));
        lore.add(line("Up to " + card.minutes() + " minutes", ChatFormatting.GRAY));
        lore.add(Component.empty());
        if (recommended) lore.add(line("Recommended for your first dive", ChatFormatting.GREEN));
        if (concerns.isEmpty()) lore.add(line("You look ready.", ChatFormatting.GREEN));
        else for (String c : concerns) for (String part : Onboarding.wrap(c, GuideMenu.WRAP)) lore.add(line(part, ChatFormatting.YELLOW));
        lore.add(Component.empty());
        lore.add(line(armed ? "Click again to dive anyway." : concerns.isEmpty() ? "Click to dive." : "Click to read this in chat; click twice to dive anyway.", armed ? ChatFormatting.RED : ChatFormatting.WHITE));
        ItemStack stack = new ItemStack(icon(card.id()));
        ChatFormatting colour = card.tier() <= 1 ? ChatFormatting.GREEN : card.tier() == 2 ? ChatFormatting.YELLOW : card.tier() == 3 ? ChatFormatting.GOLD : ChatFormatting.RED;
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(card.name()).withStyle(st -> st.withColor(colour).withItalic(false)));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        stack.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, net.minecraft.util.Unit.INSTANCE);
        if (recommended || armed) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    private static Component line(String text, ChatFormatting colour) { return Component.literal(text).withStyle(st -> st.withColor(colour).withItalic(false)); }

    private static ItemStack item(Item item, String name, ChatFormatting colour, List<String> lore, ChatFormatting loreColour, boolean glint) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, line(name, colour));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) lines.add(l.isEmpty() ? Component.empty() : line(l, loreColour));
        stack.set(DataComponents.LORE, new ItemLore(lines));
        stack.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, net.minecraft.util.Unit.INSTANCE);
        if (glint) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    private static Item icon(String type) {
        return switch (type) {
            case "normal" -> Items.WHITE_WOOL;
            case "water" -> Items.WATER_BUCKET;
            case "grass" -> Items.MOSS_BLOCK;
            case "electric" -> Items.LIGHTNING_ROD;
            case "ice" -> Items.BLUE_ICE;
            case "fighting" -> Items.IRON_AXE;
            case "poison" -> Items.SPIDER_EYE;
            case "ground" -> Items.COARSE_DIRT;
            case "flying" -> Items.FEATHER;
            case "psychic" -> Items.ENDER_EYE;
            case "bug" -> Items.COBWEB;
            case "rock" -> Items.COBBLESTONE;
            case "ghost" -> Items.SOUL_LANTERN;
            case "dragon" -> Items.DRAGON_HEAD;
            case "dark" -> Items.COAL_BLOCK;
            case "steel" -> Items.IRON_BLOCK;
            case "fairy" -> Items.PINK_TULIP;
            case "fire" -> Items.BLAZE_POWDER;
            default -> Items.MAP;
        };
    }

    private void clicked(ServerPlayer p, DiveMenu menu, SimpleContainer box, int slot) {
        if (menu == null) return;
        if (slot == DiveMenu.GUIDE) {
            // Open the guide from the next tick: a window cannot be swapped from inside its own click.
            server.execute(() -> { p.closeContainer(); GuideMenu.open(p, OnboardingStore.of(server).progress(p.getUUID()), 35); });
            return;
        }
        List<Readiness.Card> all = cards();
        if (slot < 0 || slot >= all.size() || slot >= 18) return;
        Readiness.Card card = all.get(slot);
        List<String> concerns = concerns(p, card);
        if (!concerns.isEmpty() && menu.armed != slot) {
            // Say what is wrong once; a second click on the same dungeon goes in anyway.
            menu.armed = slot;
            p.sendSystemMessage(Component.literal(card.name() + ": before you go in").withStyle(ChatFormatting.GOLD));
            for (String c : concerns) p.sendSystemMessage(Component.literal("  " + c).withStyle(ChatFormatting.YELLOW));
            p.sendSystemMessage(Component.literal("  Click it again to dive anyway.").withStyle(ChatFormatting.GRAY));
            fill(p, box, slot);
            menu.sendAllDataToRemote();
            return;
        }
        server.execute(() -> { p.closeContainer(); start(p, card); });
    }

    /** Starts a dive for this player's party. Returns the refusal, or null when the run was accepted. */
    public String start(ServerPlayer leader, Readiness.Card card) {
        RunManager m = manager.get();
        if (m == null) return tell(leader, "Dungeons are not available right now.");
        if (!book.leads(leader.getUUID())) return tell(leader, "Only your party's leader can start the dive.");
        Set<UUID> party = book.party(leader.getUUID());
        for (UUID id : party) if (server.getPlayerList().getPlayer(id) == null) return tell(leader, "A player in your party is offline. /dive party shows who; they can be dropped with /dive leave on their side, or start again with /dive leave.");
        StartResult result = m.request(party, card.id());
        if (!result.ok()) return tell(leader, result.error());
        for (UUID id : party) {
            ServerPlayer member = server.getPlayerList().getPlayer(id);
            if (member != null) member.sendSystemMessage(Component.literal((party.size() == 1 ? "Entering " : leader.getGameProfile().getName() + " is taking the party into ") + card.name()
                    + " (" + card.typeName() + ", tier " + card.tier() + "). Building it takes a moment.").withStyle(ChatFormatting.AQUA));
        }
        AscensionDungeonsLog.LOG.info("[dive] {} started {} for a party of {} (run {})", leader.getGameProfile().getName(), card.id(), party.size(), result.run().id());
        return null;
    }

    private static String tell(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.RED));
        return text;
    }

    // ---- the party -----------------------------------------------------------------------------------------------

    public boolean invite(ServerPlayer leader, ServerPlayer guest) {
        PartyBook.Result r = book.invite(leader.getUUID(), guest.getUUID(), server.getTickCount());
        String name = guest.getGameProfile().getName();
        switch (r) {
            case OK -> {
                leader.sendSystemMessage(Component.literal("Invited " + name + ". They have " + cfg.partyInviteSeconds + " seconds to accept.").withStyle(ChatFormatting.AQUA));
                guest.sendSystemMessage(Component.literal(leader.getGameProfile().getName() + " invites you to dive with them. ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal("[Accept]").withStyle(st -> st.withColor(ChatFormatting.GREEN).withBold(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/dive accept"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Join their party (/dive accept)"))))));
            }
            case SELF -> tell(leader, "You are already in your own party.");
            case FULL -> tell(leader, "Your party is full: a dive takes at most " + book.maxSize() + " players.");
            case TAKEN -> tell(leader, name + " is already in a party.");
            case NOT_LEADER -> tell(leader, "Only your party's leader can invite. /dive leave to start your own.");
            default -> tell(leader, "That invitation could not be sent.");
        }
        return r == PartyBook.Result.OK;
    }

    public boolean accept(ServerPlayer guest) {
        UUID from = book.invitedBy(guest.getUUID(), server.getTickCount());
        PartyBook.Result r = book.accept(guest.getUUID(), server.getTickCount());
        if (r != PartyBook.Result.OK) {
            tell(guest, r == PartyBook.Result.FULL ? "That party is full now." : r == PartyBook.Result.TAKEN ? "You are already in a party. /dive leave first." : "You have no invitation to accept (they last "
                    + cfg.partyInviteSeconds + " seconds).");
            return false;
        }
        for (UUID id : book.party(guest.getUUID())) {
            ServerPlayer member = server.getPlayerList().getPlayer(id);
            if (member != null) member.sendSystemMessage(Component.literal(id.equals(guest.getUUID()) ? "You joined the party. The leader chooses the dungeon with /dive."
                    : guest.getGameProfile().getName() + " joined your party.").withStyle(ChatFormatting.AQUA));
        }
        return from != null;
    }

    public boolean leave(ServerPlayer p) {
        Set<UUID> before = book.party(p.getUUID());
        boolean led = book.leads(p.getUUID());
        if (book.leave(p.getUUID()) != PartyBook.Result.OK) { tell(p, "You are not in a party."); return false; }
        for (UUID id : before) {
            ServerPlayer member = server.getPlayerList().getPlayer(id);
            if (member == null) continue;
            member.sendSystemMessage(Component.literal(id.equals(p.getUUID()) ? (led ? "You ended your party." : "You left the party.")
                    : p.getGameProfile().getName() + (led ? " ended the party." : " left the party.")).withStyle(ChatFormatting.AQUA));
        }
        return true;
    }

    /** One line naming the player's party, leader first. */
    public String describe(ServerPlayer p) {
        List<String> names = new ArrayList<>();
        for (UUID id : book.party(p.getUUID())) {
            ServerPlayer member = server.getPlayerList().getPlayer(id);
            names.add((member == null ? "(offline)" : member.getGameProfile().getName()) + (id.equals(book.leader(id)) && book.party(id).size() > 1 ? " (leader)" : ""));
        }
        return names.size() == 1 ? "You are diving alone. /dive invite <player> to bring someone." : "Your party: " + String.join(", ", names) + ".";
    }

    /** The player logged off: they leave any party, and invitations to or from them are dropped. */
    public void forget(UUID player) { book.forget(player); }

    /** Test aid: the party this player is in, leader first. */
    public Set<UUID> party(UUID player) { return book.party(player); }
}
