package com.xxxyx.cultivation.game;

import android.graphics.Color;

/**
 * 修仙九大境界。每个境界提供外观光晕颜色与基础属性倍率。
 * 境界内分 9 重，共 81 重作为修仙终点。
 */
public enum CultivationRealm {
    LIANQI("练气期", Color.rgb(0xB0, 0xC4, 0xDE), 1.00f),
    ZHUJI("筑基期", Color.rgb(0x6F, 0xE0, 0x9E), 1.35f),
    JINDAN("金丹期", Color.rgb(0xE8, 0xC2, 0x6A), 1.80f),
    YUANYING("元婴期", Color.rgb(0x6E, 0xB5, 0xFF), 2.40f),
    HUASHEN("化神期", Color.rgb(0xC9, 0x8A, 0xFF), 3.20f),
    LIANXU("炼虚期", Color.rgb(0x4F, 0xE0, 0xD8), 4.20f),
    HETI("合体期", Color.rgb(0xFF, 0x8F, 0x6E), 5.50f),
    DACHENG("大乘期", Color.rgb(0xFF, 0xD1, 0x4F), 7.20f),
    DUJIE("渡劫期", Color.rgb(0xFF, 0x5C, 0x5C), 9.50f);

    public final String name;
    public final int auraColor;
    public final float statMult;

    CultivationRealm(String name, int auraColor, float statMult) {
        this.name = name;
        this.auraColor = auraColor;
        this.statMult = statMult;
    }

    private static final CultivationRealm[] VALUES = values();

    public static CultivationRealm byIndex(int i) {
        if (i < 0) return VALUES[0];
        if (i >= VALUES.length) return VALUES[VALUES.length - 1];
        return VALUES[i];
    }

    public CultivationRealm next() {
        int i = ordinal();
        return i + 1 < VALUES.length ? VALUES[i + 1] : this;
    }

    public boolean isMax() {
        return ordinal() == VALUES.length - 1;
    }

    public static int count() {
        return VALUES.length;
    }
}
