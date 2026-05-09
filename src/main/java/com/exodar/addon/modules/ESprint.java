package com.exodar.addon.modules;

import com.exodar.addon.ExodarAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class ESprint extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> onlyForward = sgGeneral.add(new BoolSetting.Builder()
        .name("only-forward")
        .description("Only hold sprint while moving forward (W).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> blockOnSneak = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-when-sneaking")
        .description("Release sprint while sneaking.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> blockOnGui = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-in-gui")
        .description("Release sprint while a screen is open (chat, inventory).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> requireFood = sgGeneral.add(new BoolSetting.Builder()
        .name("require-food")
        .description("Stop pressing the key when hunger is too low to sprint.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> stopWhenUsing = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-when-using-item")
        .description("Release sprint while using an item (eating, drawing bow, blocking).")
        .defaultValue(true)
        .build()
    );

    public ESprint() {
        super(ExodarAddon.CATEGORY, "e-sprint", "Holds the sprint key while moving — vanilla always-sprint, no packets.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.options == null) return;

        if (blockOnGui.get() && mc.screen != null) return;
        if (stopWhenUsing.get() && mc.player.isUsingItem()) return;
        if (blockOnSneak.get() && mc.options.keyShift.isDown()) return;
        if (onlyForward.get() && !mc.options.keyUp.isDown()) return;
        if (requireFood.get() && mc.player.getFoodData().getFoodLevel() <= 6 && !mc.player.getAbilities().mayfly) return;

        mc.options.keySprint.setDown(true);
    }
}
