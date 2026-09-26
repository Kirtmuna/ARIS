package jp.apple.aris.ctc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jp.apple.aris.ArisCore;
import jp.apple.aris.util.ArisDir;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class LineManager {
    private static final Map<String, LineConfig> LOADED_LINES = new HashMap<>();
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    public static void loadAllLines() {
        File dir = ArisDir.lineDirectory;
        
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            ArisCore.LOGGER.warn("ARIS: 路線設定フォルダが見つかりません");
            return;
        }
        
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".json"));

        if (files == null || files.length == 0) {
            ArisCore.LOGGER.info("ARIS: 読み込む路線JSONがありません");
            return;
        }
        
        LOADED_LINES.clear();
        
        for (File file : files) {
            // 内部的にはファイル名から拡張子を除いたものを路線Idとして保持する
            String lineId = file.getName().substring(0, file.getName().lastIndexOf('.'));
            
            try (FileReader reader = new FileReader(file)) {
                LineConfig config = GSON.fromJson(reader, LineConfig.class);
                
                LOADED_LINES.put(lineId, config);
                ArisCore.LOGGER.info("ARIS: 路線データをロードしました: {} ({})", lineId, config.name);
            } catch (IOException e) {
                ArisCore.LOGGER.error("ARIS: 路線ファイルの読み込みに失敗しました: " + file.getName(), e);
            }
        }
        ArisCore.LOGGER.info("ARIS: 合計 {} 件の路線データをメモリに保持しました", LOADED_LINES.size());
    }

    /**
     * 他クラスから指定路線のデータを参照できるように
     * @param lineId 路線ID（ファイル名）
     * @return 保持されている LineConfig オブジェクト
     */
    public static LineConfig getLine(String lineId) {
        return LOADED_LINES.get(lineId);
    }

    /**
     * 全ての路線データのマップをそのまま返す
     */
    public static Map<String, LineConfig> getAllLines() {
        return LOADED_LINES;
    }
}
