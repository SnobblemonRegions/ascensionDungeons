package com.ascensiondungeons.mc;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side move animations and the type chart for real-time combat. Everything is vanilla particles and sounds sent
 * from the server, so no client mod is needed. Only constructed code paths touch Minecraft classes, not Cobblemon.
 */
final class MoveFx {
    private MoveFx() { }

    private static final Map<String, ParticleOptions> PARTICLE = new HashMap<>();
    private static final Map<String, SoundEvent> SOUND = new HashMap<>();
    private static final Map<String, Map<String, Double>> CHART = new HashMap<>();

    static {
        PARTICLE.put("normal", ParticleTypes.POOF);
        PARTICLE.put("fire", ParticleTypes.FLAME);
        PARTICLE.put("water", ParticleTypes.SPLASH);
        PARTICLE.put("electric", ParticleTypes.ELECTRIC_SPARK);
        PARTICLE.put("grass", ParticleTypes.COMPOSTER);
        PARTICLE.put("ice", ParticleTypes.SNOWFLAKE);
        PARTICLE.put("fighting", ParticleTypes.CRIT);
        PARTICLE.put("poison", ParticleTypes.WITCH);
        PARTICLE.put("ground", new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()));
        PARTICLE.put("flying", ParticleTypes.CLOUD);
        PARTICLE.put("psychic", ParticleTypes.ENCHANT);
        PARTICLE.put("bug", ParticleTypes.SPORE_BLOSSOM_AIR);
        PARTICLE.put("rock", new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()));
        PARTICLE.put("ghost", ParticleTypes.SOUL);
        PARTICLE.put("dragon", ParticleTypes.DRAGON_BREATH);
        PARTICLE.put("dark", ParticleTypes.SQUID_INK);
        PARTICLE.put("steel", ParticleTypes.WAX_OFF);
        PARTICLE.put("fairy", ParticleTypes.END_ROD);

        SOUND.put("normal", SoundEvents.PLAYER_ATTACK_STRONG);
        SOUND.put("fire", SoundEvents.BLAZE_SHOOT);
        SOUND.put("water", SoundEvents.PLAYER_SPLASH);
        SOUND.put("electric", SoundEvents.LIGHTNING_BOLT_IMPACT);
        SOUND.put("grass", SoundEvents.GRASS_BREAK);
        SOUND.put("ice", SoundEvents.GLASS_BREAK);
        SOUND.put("fighting", SoundEvents.PLAYER_ATTACK_CRIT);
        SOUND.put("poison", SoundEvents.SPIDER_HURT);
        SOUND.put("ground", SoundEvents.GRAVEL_BREAK);
        SOUND.put("flying", SoundEvents.PHANTOM_FLAP);
        SOUND.put("psychic", SoundEvents.ENDERMAN_TELEPORT);
        SOUND.put("bug", SoundEvents.SILVERFISH_AMBIENT);
        SOUND.put("rock", SoundEvents.STONE_BREAK);
        SOUND.put("ghost", SoundEvents.SOUL_ESCAPE.value());
        SOUND.put("dragon", SoundEvents.ENDER_DRAGON_GROWL);
        SOUND.put("dark", SoundEvents.WITHER_SHOOT);
        SOUND.put("steel", SoundEvents.ANVIL_LAND);
        SOUND.put("fairy", SoundEvents.AMETHYST_BLOCK_CHIME);

        chart("normal", "rock=.5,steel=.5,ghost=0");
        chart("fire", "fire=.5,water=.5,rock=.5,dragon=.5,grass=2,ice=2,bug=2,steel=2");
        chart("water", "water=.5,grass=.5,dragon=.5,fire=2,ground=2,rock=2");
        chart("electric", "electric=.5,grass=.5,dragon=.5,ground=0,water=2,flying=2");
        chart("grass", "fire=.5,grass=.5,poison=.5,flying=.5,bug=.5,dragon=.5,steel=.5,water=2,ground=2,rock=2");
        chart("ice", "fire=.5,water=.5,ice=.5,steel=.5,grass=2,ground=2,flying=2,dragon=2");
        chart("fighting", "poison=.5,flying=.5,psychic=.5,bug=.5,fairy=.5,ghost=0,normal=2,ice=2,rock=2,dark=2,steel=2");
        chart("poison", "poison=.5,ground=.5,rock=.5,ghost=.5,steel=0,grass=2,fairy=2");
        chart("ground", "grass=.5,bug=.5,flying=0,fire=2,electric=2,poison=2,rock=2,steel=2");
        chart("flying", "electric=.5,rock=.5,steel=.5,grass=2,fighting=2,bug=2");
        chart("psychic", "psychic=.5,steel=.5,dark=0,fighting=2,poison=2");
        chart("bug", "fire=.5,fighting=.5,poison=.5,flying=.5,ghost=.5,steel=.5,fairy=.5,grass=2,psychic=2,dark=2");
        chart("rock", "fighting=.5,ground=.5,steel=.5,fire=2,ice=2,flying=2,bug=2");
        chart("ghost", "normal=0,dark=.5,ghost=2,psychic=2");
        chart("dragon", "steel=.5,fairy=0,dragon=2");
        chart("dark", "fighting=.5,dark=.5,fairy=.5,ghost=2,psychic=2");
        chart("steel", "fire=.5,water=.5,electric=.5,steel=.5,ice=2,rock=2,fairy=2");
        chart("fairy", "fire=.5,poison=.5,steel=.5,fighting=2,dragon=2,dark=2");
    }

    private static void chart(String attacker, String spec) {
        Map<String, Double> row = new HashMap<>();
        for (String entry : spec.split(",")) {
            String[] kv = entry.split("=");
            row.put(kv[0], Double.parseDouble(kv[1]));
        }
        CHART.put(attacker, row);
    }

    /** Multiplier of one attacking type against one defending type (1.0 when unlisted). */
    static double effectiveness(String attackType, String defendType) {
        Map<String, Double> row = CHART.get(attackType);
        return row == null ? 1.0 : row.getOrDefault(defendType, 1.0);
    }

    private static ParticleOptions particle(String type) { return PARTICLE.getOrDefault(type, ParticleTypes.POOF); }

    private static SoundEvent sound(String type) { return SOUND.getOrDefault(type, SoundEvents.PLAYER_ATTACK_STRONG); }

    /** Wind-up at the attacker, a beam of the move's particles to the target, then an impact burst. */
    static void ranged(ServerLevel level, String type, Vec3 from, Vec3 to) {
        ParticleOptions p = particle(type);
        level.sendParticles(p, from.x, from.y, from.z, 8, 0.25, 0.25, 0.25, 0.02);
        Vec3 d = to.subtract(from);
        int steps = Math.max(2, (int) (d.length() * 2));
        for (int i = 1; i < steps; i++) {
            Vec3 at = from.add(d.scale((double) i / steps));
            level.sendParticles(p, at.x, at.y, at.z, 2, 0.06, 0.06, 0.06, 0.0);
        }
        level.sendParticles(ParticleTypes.FLASH, to.x, to.y, to.z, 1, 0, 0, 0, 0);
        impact(level, type, to);
        level.playSound(null, from.x, from.y, from.z, sound(type), SoundSource.HOSTILE, 1.0f, 1.0f);
    }

    /** Close-range strike: a sweep through the target plus the type's burst. */
    static void melee(ServerLevel level, String type, Vec3 from, Vec3 to) {
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, to.x, to.y, to.z, 1, 0, 0, 0, 0);
        impact(level, type, to);
        level.playSound(null, to.x, to.y, to.z, sound(type), SoundSource.HOSTILE, 1.0f, 0.9f);
    }

    private static void impact(ServerLevel level, String type, Vec3 at) {
        level.sendParticles(particle(type), at.x, at.y, at.z, 18, 0.4, 0.4, 0.4, 0.08);
    }
}
