package jp.apple.aris.ctc.enums;

import jp.apple.aris.ArisCore;

public enum SignalType {
    // [停止, 警戒, 注意, 減速, 進行, 高速進行] の順で数値（レベル）をマッピング
    TWO_ASPECTS_A(new int[]{1, -1, -1, 2, -1, -1}),  // 2灯式A (停止=1, 注意=2)
    TWO_ASPECTS_B(new int[]{1, -1, -1, -1, 2, -1}),  // 2灯式B (停止=1, 進行=2)
    THREE_ASPECTS(new int[]{1, -1, 2, -1, 4, -1}),   // 3灯式  (停止=1, 注意=[2,3], 進行=4)
    THREE_ASPECTS_A(new int[]{1, -1, 2, -1, -1, -1}),// 3灯式A (停止=1, 注意=2)
    THREE_ASPECTS_B(new int[]{1, -1, -1, -1, 2, -1}),// 3灯式B (停止=1, 進行=2)
    FOUR_ASPECTS(new int[]{1, 2, -1, -1, 3, -1}),    // 4灯式  (停止=1, 警戒=2, 進行=3)
    FOUR_ASPECTS_A(new int[]{1, 2, 3, -1, 4, -1}),   // 4灯式A (停止=1, 警戒=2, 注意=3, 進行=4)
    FOUR_ASPECTS_B(new int[]{1, -1, 2, 4, 5, -1}),   // 4灯式B (停止=1, 注意=[2,3], 減速=4, 進行=5)
    FIVE_ASPECTS_A(new int[]{1, 2, 3, 4, 5, -1}),    // 5灯式A (停止=1, 警戒=2, 注意=3, 減速=4, 進行=5)
    FIVE_ASPECTS_B(new int[]{1, -1, 2, -1, 4, 6}),  // 5灯式B (停止=1, 注意=[2,3], 進行=[4,5], 高速進行=6)
    SIX_ASPECTS(new int[]{1, -1, 2, 4, 5, 6});       // 6灯式  (停止=1, 注意=[2,3], 減速=4, 進行=5, 高速進行=6)

    private final int[] Levels;

    SignalType(int[] Levels) {
        this.Levels = Levels;
    }

    /**
     * JSONの "type" 文字列から対応する enum を特定する
     */
    public static SignalType fromKey(String key) {
        if (key == null) return THREE_ASPECTS;

        String normalized = key.toUpperCase()
                .replace("_", "").replace(" ", "");

        switch (normalized) {
            case "2A":
                return TWO_ASPECTS_A;
            case "2B":
                return TWO_ASPECTS_B;
            case "3":
                return THREE_ASPECTS;
            case "3A":
                return THREE_ASPECTS_A;
            case "3B":
                return THREE_ASPECTS_B;
            case "4":
                return FOUR_ASPECTS;
            case "4A":
                return FOUR_ASPECTS_A;
            case "4B":
                return FOUR_ASPECTS_B;
            case "5A":
                return FIVE_ASPECTS_A;
            case "5B":
                return FIVE_ASPECTS_B;
            case "6":
                return SIX_ASPECTS;
            default:
                ArisCore.LOGGER.warn("ARIS: 未知の信号タイプ '{}' です。3灯式をデフォルトにします。", key);
                return THREE_ASPECTS;
        }
    }

    /**
     * 要求された現示に対応するシグナルレベルを配列から取得する
     *
     * @param aspectIndex 0=停止, 1=警戒, 2=注意, 3=減速, 4=進行, 5=高速進行
     * @return 対応する数値
     */
    public int getLevel(int aspectIndex) {
        // 範囲外、またはその灯式に対応する現示がない場合
        if (aspectIndex < 0 || aspectIndex >= this.Levels.length || this.Levels[aspectIndex] == -1) {
            return this.Levels[0];
        }
        return this.Levels[aspectIndex];
    }
}
