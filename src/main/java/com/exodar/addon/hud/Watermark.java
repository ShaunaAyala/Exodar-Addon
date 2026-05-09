package com.exodar.addon.hud;

import com.exodar.addon.ExodarAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

public class Watermark extends HudElement {
    public static final HudElementInfo<Watermark> INFO = new HudElementInfo<>(
        ExodarAddon.HUD_GROUP,
        "exodar-watermark",
        "Exodar branding watermark with version, FPS and ping.",
        Watermark::new
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");

    private final Setting<String> brand = sgGeneral.add(new StringSetting.Builder()
        .name("brand")
        .description("Watermark brand text.")
        .defaultValue("Exodar")
        .build()
    );

    private final Setting<Boolean> showVersion = sgGeneral.add(new BoolSetting.Builder()
        .name("show-version")
        .description("Show Minecraft version in brackets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showFps = sgGeneral.add(new BoolSetting.Builder()
        .name("show-fps")
        .description("Show FPS in brackets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showPing = sgGeneral.add(new BoolSetting.Builder()
        .name("show-ping")
        .description("Show ping in brackets (multiplayer only).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow")
        .description("Render text with shadow.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> brandColor = sgColors.add(new ColorSetting.Builder()
        .name("brand-color")
        .description("Brand text color.")
        .defaultValue(new SettingColor(160, 32, 240))
        .build()
    );

    private final Setting<SettingColor> infoColor = sgColors.add(new ColorSetting.Builder()
        .name("info-color")
        .description("Info text color (version, fps, ping numbers).")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> bracketColor = sgColors.add(new ColorSetting.Builder()
        .name("bracket-color")
        .description("Bracket color.")
        .defaultValue(new SettingColor(140, 140, 140))
        .build()
    );

    public Watermark() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        Minecraft mc = Minecraft.getInstance();
        boolean sh = shadow.get();

        String brandStr = brand.get();
        String version = SharedConstants.getCurrentVersion().name();
        int fps = mc.getFps();
        int ping = pingMs(mc);

        double cx = x;
        double y0 = y;

        cx += renderer.text(brandStr, cx, y0, brandColor.get(), sh);
        if (showVersion.get()) {
            cx += renderer.text(" ", cx, y0, infoColor.get(), sh);
            cx += renderer.text("[", cx, y0, bracketColor.get(), sh);
            cx += renderer.text(version, cx, y0, infoColor.get(), sh);
            cx += renderer.text("]", cx, y0, bracketColor.get(), sh);
        }
        if (showPing.get() && mc.getConnection() != null && mc.player != null) {
            cx += renderer.text(" ", cx, y0, infoColor.get(), sh);
            cx += renderer.text("[", cx, y0, bracketColor.get(), sh);
            cx += renderer.text(ping + "ms", cx, y0, infoColor.get(), sh);
            cx += renderer.text("]", cx, y0, bracketColor.get(), sh);
        }
        if (showFps.get()) {
            cx += renderer.text(" ", cx, y0, infoColor.get(), sh);
            cx += renderer.text("[", cx, y0, bracketColor.get(), sh);
            cx += renderer.text(fps + "fps", cx, y0, infoColor.get(), sh);
            cx += renderer.text("]", cx, y0, bracketColor.get(), sh);
        }

        setSize(cx - x, renderer.textHeight(sh));
    }

    private static int pingMs(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) return 0;
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : info.getLatency();
    }
}
