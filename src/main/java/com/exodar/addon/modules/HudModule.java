package com.exodar.addon.modules;

import com.exodar.addon.ExodarAddon;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HudModule extends Module {
    private record EnchantEntry(String label, ResourceKey<Enchantment> key) {}

    private static final List<EnchantEntry> ARMOR_ENCHANTS = List.of(
        new EnchantEntry("Prot", Enchantments.PROTECTION),
        new EnchantEntry("Unb", Enchantments.UNBREAKING),
        new EnchantEntry("Men", Enchantments.MENDING),
        new EnchantEntry("Th", Enchantments.THORNS)
    );

    private static final List<EnchantEntry> WEAPON_ENCHANTS = List.of(
        new EnchantEntry("Sh", Enchantments.SHARPNESS),
        new EnchantEntry("Sm", Enchantments.SMITE),
        new EnchantEntry("Lo", Enchantments.LOOTING),
        new EnchantEntry("Fi", Enchantments.FIRE_ASPECT),
        new EnchantEntry("Unb", Enchantments.UNBREAKING),
        new EnchantEntry("Men", Enchantments.MENDING)
    );

    private static final List<EnchantEntry> TOOL_ENCHANTS = List.of(
        new EnchantEntry("Ef", Enchantments.EFFICIENCY),
        new EnchantEntry("Fo", Enchantments.FORTUNE),
        new EnchantEntry("Si", Enchantments.SILK_TOUCH),
        new EnchantEntry("Unb", Enchantments.UNBREAKING),
        new EnchantEntry("Men", Enchantments.MENDING)
    );

    private static final List<EnchantEntry> BOW_ENCHANTS = List.of(
        new EnchantEntry("Po", Enchantments.POWER),
        new EnchantEntry("Pu", Enchantments.PUNCH),
        new EnchantEntry("Fl", Enchantments.FLAME),
        new EnchantEntry("In", Enchantments.INFINITY),
        new EnchantEntry("Unb", Enchantments.UNBREAKING),
        new EnchantEntry("Men", Enchantments.MENDING)
    );

    private final SettingGroup sgArmor = settings.createGroup("Armor");
    private final SettingGroup sgPotion = settings.createGroup("Potion");

    private final Setting<Boolean> showArmor = sgArmor.add(new BoolSetting.Builder()
        .name("show-armor").description("Render armor + hands above food bar.").defaultValue(true).build());

    private final Setting<Boolean> showMainhand = sgArmor.add(new BoolSetting.Builder()
        .name("show-mainhand").description("Render mainhand slot.").defaultValue(true).build());

    private final Setting<Boolean> showOffhand = sgArmor.add(new BoolSetting.Builder()
        .name("show-offhand").description("Render offhand slot.").defaultValue(true).build());

    private final Setting<Boolean> showDurabilityBar = sgArmor.add(new BoolSetting.Builder()
        .name("durability-bar").description("Render the durability bar under each slot.").defaultValue(true).build());

    private final Setting<Boolean> showEnchants = sgArmor.add(new BoolSetting.Builder()
        .name("show-enchants").description("Show key enchant levels under each slot.").defaultValue(true).build());

    private final Setting<Integer> maxEnchants = sgArmor.add(new IntSetting.Builder()
        .name("max-enchants").description("Max enchants per slot.").defaultValue(4).range(1, 8).sliderRange(1, 8).build());

    private final Setting<SettingColor> enchantColor = sgArmor.add(new ColorSetting.Builder()
        .name("enchant-color").description("Enchant text color.").defaultValue(new SettingColor(160, 32, 240)).build());

    private final Setting<Boolean> showPotions = sgPotion.add(new BoolSetting.Builder()
        .name("show-potions").description("Render active potion effects top-left.").defaultValue(true).build());

    private final Setting<Boolean> hideAmbient = sgPotion.add(new BoolSetting.Builder()
        .name("hide-ambient").description("Skip ambient effects (beacon, conduit).").defaultValue(false).build());

    private final Setting<Boolean> showAmplifier = sgPotion.add(new BoolSetting.Builder()
        .name("show-amplifier").description("Append roman amplifier (II, III...).").defaultValue(true).build());

    private final Setting<Integer> lowThreshold = sgPotion.add(new IntSetting.Builder()
        .name("low-threshold-sec").description("Seconds at which duration switches to red.").defaultValue(10).range(1, 120).sliderRange(1, 120).build());

    public HudModule() {
        super(ExodarAddon.CATEGORY, "hud", "Armor + hands HUD above food bar and potion effects top-left.");
    }

    @Override
    public void onActivate() {
        info("Hud activated.");
    }

    @Override
    public void onDeactivate() {
        info("Hud deactivated.");
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        if (event.graphics == null) return;
        if (mc.player == null || mc.options == null || mc.level == null) return;
        if (mc.options.hideGui) return;

        GuiGraphicsExtractor g = event.graphics;
        int sw = event.screenWidth;
        int sh = event.screenHeight;

        g.fill(sw / 2 - 2, 2, sw / 2 + 2, 6, 0xFFA020F0);

        if (showArmor.get()) renderArmor(g, sw, sh);
        if (showPotions.get()) renderPotions(g);
    }

    private void renderArmor(GuiGraphicsExtractor g, int sw, int sh) {
        g.fill(20, 20, 60, 60, 0xFF00FF00);
        List<EquipmentSlot> slots = new ArrayList<>(6);
        slots.add(EquipmentSlot.HEAD);
        slots.add(EquipmentSlot.CHEST);
        slots.add(EquipmentSlot.LEGS);
        slots.add(EquipmentSlot.FEET);
        if (showMainhand.get()) slots.add(EquipmentSlot.MAINHAND);
        if (showOffhand.get()) slots.add(EquipmentSlot.OFFHAND);

        int n = slots.size();
        int stride = 18;
        int totalW = n * stride;
        int baseX = sw / 2 + 91 - totalW;
        int baseY = sh - 55;

        g.fill(baseX - 2, baseY - 2, baseX + totalW + 2, baseY + 18, 0xFFFF0000);

        for (int i = 0; i < n; i++) {
            ItemStack stack = mc.player.getItemBySlot(slots.get(i));
            int sx = baseX + i * stride;
            int sy = baseY;

            g.fill(sx, sy, sx + 16, sy + 16, 0xFF000000);
            if (stack.isEmpty()) continue;
            g.item(stack, sx, sy);

            if (showDurabilityBar.get() && stack.isDamageableItem() && stack.getMaxDamage() > 0) {
                int max = stack.getMaxDamage();
                int dmg = stack.getDamageValue();
                float pct = Math.max(0f, Math.min(1f, 1f - (float) dmg / max));
                int barY = sy + 16 + 1;
                int barW = (int) (16 * pct);
                int color = packColor(lerpColor(255, 0, 0, 0, 230, 0, pct), 230);
                g.fill(sx, barY, sx + 16, barY + 2, 0xC8282828);
                if (barW > 0) g.fill(sx, barY, sx + barW, barY + 2, color);
            }

            if (showEnchants.get()) {
                List<EnchantEntry> table = pickEnchantTable(stack, slots.get(i));
                int textY = sy + 16 + (showDurabilityBar.get() ? 4 : 1);
                int rendered = 0;
                int limit = maxEnchants.get();
                int packedColor = enchantColor.get().getPacked();
                for (EnchantEntry e : table) {
                    if (rendered >= limit) break;
                    int level = enchantLevel(stack, e.key());
                    if (level <= 0) continue;
                    g.text(mc.font, e.label() + level, sx, textY + rendered * 8, packedColor, true);
                    rendered++;
                }
            }
        }
    }

    private void renderPotions(GuiGraphicsExtractor g) {
        List<MobEffectInstance> effects = new ArrayList<>(mc.player.getActiveEffects());
        if (hideAmbient.get()) effects.removeIf(MobEffectInstance::isAmbient);
        if (effects.isEmpty()) return;
        effects.sort(Comparator.comparingInt(MobEffectInstance::getDuration));

        float tickrate = (mc.level == null) ? 20f : mc.level.tickRateManager().tickrate();
        int textLineH = mc.font.lineHeight;
        int icon = 14;
        int gap = 4;
        int rowH = Math.max(icon, 18);
        int rowSpacing = 2;
        int low = lowThreshold.get();

        int baseX = 4;
        int baseY = 4;

        for (int i = 0; i < effects.size(); i++) {
            MobEffectInstance e = effects.get(i);
            int rx = baseX;
            int ry = baseY + i * (rowH + rowSpacing);

            int rgb = e.getEffect().value().getColor();
            int packed = (220 << 24) | (rgb & 0x00FFFFFF);
            g.fill(rx, ry, rx + icon, ry + icon, packed);

            String name = e.getEffect().value().getDisplayName().getString();
            if (showAmplifier.get() && e.getAmplifier() > 0) {
                name = name + " " + roman(e.getAmplifier() + 1);
            }
            String dur = StringUtil.formatTickDuration(e.getDuration(), tickrate);

            int textX = rx + icon + gap;
            int nameY = ry + 1;
            g.text(mc.font, name, textX, nameY, 0xFFFFFFFF, true);

            int durY = nameY + textLineH + 1;
            int durColor = (e.getDuration() / Math.max(1f, tickrate)) < low ? 0xFFFF5050 : 0xFFB4B4B4;
            g.text(mc.font, dur, textX, durY, durColor, true);
        }
    }

    private List<EnchantEntry> pickEnchantTable(ItemStack stack, EquipmentSlot slot) {
        if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR || slot.getType() == EquipmentSlot.Type.ANIMAL_ARMOR) {
            return ARMOR_ENCHANTS;
        }
        String id = stack.getItem().toString().toLowerCase();
        if (id.contains("bow") || id.contains("crossbow")) return BOW_ENCHANTS;
        if (id.contains("pickaxe") || id.contains("shovel") || id.contains("hoe") || id.contains("shears") || id.contains("fishing_rod")) return TOOL_ENCHANTS;
        return WEAPON_ENCHANTS;
    }

    private int enchantLevel(ItemStack stack, ResourceKey<Enchantment> key) {
        if (mc.level == null) return 0;
        try {
            Holder<Enchantment> holder = mc.level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(key);
            return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
        } catch (Exception ex) {
            return 0;
        }
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(n);
        };
    }

    private static int lerpColor(int r1, int g1, int b1, int r2, int g2, int b2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static int packColor(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }
}
