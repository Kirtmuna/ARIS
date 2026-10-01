package jp.apple.aris.ctc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jp.apple.aris.ArisCore;
import jp.apple.aris.util.ArisDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
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
            
            try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                LineConfig config = GSON.fromJson(reader, LineConfig.class);
                if (config == null) {
                    ArisCore.LOGGER.warn("ARIS: 路線ファイルの内容が空です: {}", file.getName());
                    continue;
                }
                
                LOADED_LINES.put(lineId, config);
                ArisCore.LOGGER.info("ARIS: 路線データをロードしました: {} ({})", lineId, config.name);
            } catch (IOException e) {
                ArisCore.LOGGER.error("ARIS: 路線ファイルの読み込みに失敗しました: " + file.getName(), e);
            }
        }
        ArisCore.LOGGER.info("ARIS: 合計 {} 件の路線データをメモリに保持しました", LOADED_LINES.size());
    }
    /**
     * 指定された路線の設計図を、元のJSONファイルに上書き保存する
     * @param lineId 路線ID（ファイル名）
     */
    public static void saveLine(String lineId) {
        LineConfig config = LOADED_LINES.get(lineId);
        if (config == null) return;

        File file = new File(ArisDir.lineDirectory, lineId + ".json");

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
            ArisCore.LOGGER.info("ARIS: レールのコア座標への自動書換が完了しました: {}.json", lineId);
        } catch (IOException e) {
            ArisCore.LOGGER.error("ARIS: 路線ファイルの自動書換に失敗しました: " + file.getName(), e);
        }
    }
    /**
     * 指定路線のJSONファイルをディスクから再読込し、メモリ上のキャッシュを最新化する。
     * ツールで書き換える直前に呼び、手動編集の内容を取りこぼさないようにする。
     * @return 読み込めたらtrue、ファイルが無い/失敗したらfalse
     */
    public static boolean reloadLine(String lineId) {
        File file = new File(ArisDir.lineDirectory, lineId + ".json");
        if (!file.exists()) {
            return false;
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            LineConfig config = GSON.fromJson(reader, LineConfig.class);
            if (config == null) {
                ArisCore.LOGGER.warn("ARIS: 路線ファイルの内容が空です: {}", file.getName());
                return false;
            }
            LOADED_LINES.put(lineId, config);
            return true;
        } catch (IOException e) {
            ArisCore.LOGGER.error("ARIS: 路線ファイルの再読込に失敗しました: " + file.getName(), e);
            return false;
        }
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
