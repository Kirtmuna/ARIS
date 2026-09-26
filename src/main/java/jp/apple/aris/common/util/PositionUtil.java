package jp.apple.aris.common.util;

import net.minecraft.util.math.BlockPos;

public class PositionUtil {
    /**
     * 配列-座標 を BlockPos に変換する
     * @param posArray [x, y, z] の整数配列
     * @return 変換された BlockPos（データが不正なら BlockPos.ORIGIN）
     */
    public static BlockPos toBlockPos(int[] posArray) {
        if (posArray == null || posArray.length < 3) {
            return BlockPos.ORIGIN;
        }
        return new BlockPos(posArray[0], posArray[1], posArray[2]);
    }
    /**
     * 二重配列-座標を BlockPos[] の配列に変換する
     * @param pos2DArray 二重配列のデータ
     * @return BlockPos配列
     */
    public static BlockPos[] toBlockPosArray(int[][] pos2DArray) {
        if (pos2DArray == null || pos2DArray.length == 0) {
            return new BlockPos[0];
        }
        BlockPos[] result = new BlockPos[pos2DArray.length];
        
        for (int i = 0; i < pos2DArray.length; i++) {
            result[i] = toBlockPos(pos2DArray[i]);
        }
        return result;
    }
}
