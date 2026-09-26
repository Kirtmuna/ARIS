package jp.apple.aris.util;

import jp.apple.aris.ArisCore;

import java.io.File;

public class ArisDir {
    public static File lineDirectory;
    
    public static void init(File modsDir) {
        lineDirectory = new File(modsDir, "aris" + File.separator + "line");

        if (!lineDirectory.exists()) {
            boolean success = lineDirectory.mkdirs();
            if (success) {
                ArisCore.LOGGER.info("ARIS: Lineフォルダを作成しました: {}", lineDirectory.getAbsolutePath());
            } else {
                ArisCore.LOGGER.error("ARIS: Lineフォルダの作成に失敗しました");
            }
        } else {
            ArisCore.LOGGER.info("ARIS: Lineフォルダを確認しました: {}", lineDirectory.getAbsolutePath());
        }
    }
}
