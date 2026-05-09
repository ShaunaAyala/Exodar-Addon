package com.exodar.addon.hud;

import com.exodar.addon.ExodarAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ModuleListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ArrayListHud extends HudElement {
    public static final HudElementInfo<ArrayListHud> INFO = new HudElementInfo<>(
        ExodarAddon.HUD_GROUP,
        "exodar-arraylist",
        "Active modules list. Honors a per-element hide list.",
        ArrayListHud::new
    );

    public enum Sort { Length, Alphabetical, None }
    public enum Alignment { Left, Right }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");

    private final Setting<List<Module>> hiddenModules = sgGeneral.add(new ModuleListSetting.Builder()
        .name("hidden-modules")
        .description("Modules that won't appear in the list.")
        .defaultValue(List.of())
        .build()
    );

    private final Setting<Sort> sortMode = sgGeneral.add(new EnumSetting.Builder<Sort>()
        .name("sort")
        .description("Sort order for the list.")
        .defaultValue(Sort.Length)
        .build()
    );

    private final Setting<Alignment> alignment = sgGeneral.add(new EnumSetting.Builder<Alignment>()
        .name("alignment")
        .description("Anchor side for each row.")
        .defaultValue(Alignment.Right)
        .build()
    );

    private final Setting<Boolean> showInfo = sgGeneral.add(new BoolSetting.Builder()
        .name("show-info")
        .description("Append the module info string in brackets (e.g. mode).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow")
        .description("Render text with shadow.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> rowSpacing = sgGeneral.add(new IntSetting.Builder()
        .name("line-spacing")
        .description("Pixels between lines.")
        .defaultValue(0)
        .range(0, 8)
        .sliderRange(0, 8)
        .build()
    );

    private final Setting<SettingColor> moduleColor = sgColors.add(new ColorSetting.Builder()
        .name("module-color")
        .description("Module name color.")
        .defaultValue(new SettingColor(160, 32, 240))
        .build()
    );

    private final Setting<SettingColor> infoColor = sgColors.add(new ColorSetting.Builder()
        .name("info-color")
        .description("Info string color.")
        .defaultValue(new SettingColor(180, 180, 180))
        .build()
    );

    private final Setting<SettingColor> bracketColor = sgColors.add(new ColorSetting.Builder()
        .name("bracket-color")
        .description("Bracket color around info string.")
        .defaultValue(new SettingColor(140, 140, 140))
        .build()
    );

    public ArrayListHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        boolean sh = shadow.get();
        double lineH = renderer.textHeight(sh);
        int rg = rowSpacing.get();

        List<Module> modules = new ArrayList<>(Modules.get().getActive());
        modules.removeAll(hiddenModules.get());

        if (modules.isEmpty()) {
            setSize(0, 0);
            return;
        }

        int n = modules.size();
        String[] names = new String[n];
        String[] infos = new String[n];
        double[] nameW = new double[n];
        double[] infoW = new double[n];
        double[] rowW = new double[n];

        for (int i = 0; i < n; i++) {
            Module m = modules.get(i);
            names[i] = m.title;
            String info = showInfo.get() ? m.getInfoString() : null;
            infos[i] = (info != null && !info.isEmpty()) ? info : null;
            nameW[i] = renderer.textWidth(names[i], sh);
            infoW[i] = infos[i] == null ? 0 : renderer.textWidth(" [" + infos[i] + "]", sh);
            rowW[i] = nameW[i] + infoW[i];
        }

        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;

        switch (sortMode.get()) {
            case Length -> {
                final double[] w = rowW;
                java.util.Arrays.sort(order, (a, b) -> Double.compare(w[b], w[a]));
            }
            case Alphabetical -> {
                final String[] nm = names;
                java.util.Arrays.sort(order, Comparator.comparing(i -> nm[i].toLowerCase()));
            }
            case None -> { /* keep getActive order */ }
        }

        double maxW = 0;
        for (double w : rowW) maxW = Math.max(maxW, w);
        double totalH = n * lineH + Math.max(0, n - 1) * rg;
        setSize(maxW, totalH);

        boolean rightAlign = alignment.get() == Alignment.Right;
        for (int row = 0; row < n; row++) {
            int i = order[row];
            double tw = rowW[i];
            double tx = rightAlign ? (x + maxW - tw) : x;
            double ty = y + row * (lineH + rg);

            double cursor = tx;
            cursor += renderer.text(names[i], cursor, ty, moduleColor.get(), sh);
            if (infos[i] != null) {
                cursor += renderer.text(" [", cursor, ty, bracketColor.get(), sh);
                cursor += renderer.text(infos[i], cursor, ty, infoColor.get(), sh);
                renderer.text("]", cursor, ty, bracketColor.get(), sh);
            }
        }
    }
}
