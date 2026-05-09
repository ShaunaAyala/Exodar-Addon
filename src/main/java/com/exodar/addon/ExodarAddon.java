package com.exodar.addon;

import com.exodar.addon.hud.ArrayListHud;
import com.exodar.addon.hud.Watermark;
import com.exodar.addon.modules.AimAssist;
import com.exodar.addon.modules.AutoClicker;
import com.exodar.addon.modules.ESprint;
import com.exodar.addon.modules.ETool;
import com.exodar.addon.modules.HudModule;
import com.exodar.addon.modules.JumpReset;
import com.exodar.addon.modules.LegitAura;
import com.exodar.addon.modules.Penetration;
import com.exodar.addon.modules.TriggerBot;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class ExodarAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Exodar");
    public static final HudGroup HUD_GROUP = new HudGroup("E Hud");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Exodar");
        Modules.get().add(new ESprint());
        Modules.get().add(new ETool());
        Modules.get().add(new AimAssist());
        Modules.get().add(new TriggerBot());
        Modules.get().add(new AutoClicker());
        Modules.get().add(new LegitAura());
        Modules.get().add(new Penetration());

        // Unfinished things — pending fixes before public release
        // Modules.get().add(new HudModule());
        // Modules.get().add(new JumpReset());

        Hud.get().register(Watermark.INFO);
        Hud.get().register(ArrayListHud.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.exodar.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("ShaunaAyala", "Exodar-Addon");
    }
}
