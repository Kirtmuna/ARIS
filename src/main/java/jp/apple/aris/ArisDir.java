package jp.apple.aris;

import java.io.File;

public class ArisDir {
    /** 路線データファイル (world/aris/data.json) */
    public static File dataFile;
    /** キャッシュディレクトリ (world/aris/cache/) */
    public static File cacheDirectory;
    /** aris ディレクトリ本体 (world/aris/) */
    public static File rootDirectory;
    
    public static void init(File modsDir) {
    }
    
    public static void initWorld(File worldDir) {
        rootDirectory = new File(worldDir, "aris");
        if (!rootDirectory.exists()) {
            if (rootDirectory.mkdirs()) {
                ArisCore.LOGGER.info("ARIS: arisフォルダを作成しました: {}", rootDirectory.getAbsolutePath());
            } else {
                ArisCore.LOGGER.error("ARIS: arisフォルダの作成に失敗しました");
            }
        } else {
            ArisCore.LOGGER.info("ARIS: arisフォルダを確認しました: {}", rootDirectory.getAbsolutePath());
        }
        dataFile = new File(rootDirectory, "data.json");
        cacheDirectory = new File(rootDirectory, "cache");
        if (!cacheDirectory.exists()) cacheDirectory.mkdirs();
    }
}
