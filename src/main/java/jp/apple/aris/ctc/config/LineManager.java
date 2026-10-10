package jp.apple.aris.ctc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jp.apple.aris.ArisCore;
import jp.apple.aris.ArisDir;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class LineManager {
    /** 単一の路線データ。路線の概念は廃止したため、常に1つ */
    private static LineConfig CONFIG = new LineConfig();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * data.json を読み込んでメモリ上の CONFIG を更新する。
     * ファイルが存在しない場合は空の CONFIG で開始する。
     */
    public static void load() {
        File file = ArisDir.dataFile;
        if (file == null || !file.exists()) {
            ArisCore.LOGGER.info("ARIS: data.json が存在しないため、空の路線データで開始します");
            CONFIG = new LineConfig();
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            LineConfig cfg = GSON.fromJson(reader, LineConfig.class);
            if (cfg == null) {
                ArisCore.LOGGER.warn("ARIS: data.json の内容が空です");
                CONFIG = new LineConfig();
                return;
            }
            CONFIG = cfg;
            ArisCore.LOGGER.info("ARIS: 路線データをロードしました");
        } catch (IOException e) {
            ArisCore.LOGGER.error("ARIS: 路線ファイルの読み込みに失敗しました", e);
        }
    }

    /**
     * 指定された路線の設計図を data.json に上書き保存する
     */
    public static void save() {
        File file = ArisDir.dataFile;
        if (file == null) {
            ArisCore.LOGGER.error("ARIS: data.json のパスが未初期化です");
            return;
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(CONFIG, writer);
        } catch (IOException e) {
            ArisCore.LOGGER.error("ARIS: 路線ファイルの保存に失敗しました", e);
        }
    }

    /**
     * data.json をディスクから再読込してメモリ上の CONFIG を最新化する。
     * @return 読み込めたら true、ファイルが無い/失敗したら false
     */
    public static boolean reload() {
        File file = ArisDir.dataFile;
        if (file == null || !file.exists()) {
            return false;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            LineConfig cfg = GSON.fromJson(reader, LineConfig.class);
            if (cfg == null) {
                ArisCore.LOGGER.warn("ARIS: data.json の内容が空です");
                return false;
            }
            CONFIG = cfg;
            return true;
        } catch (IOException e) {
            ArisCore.LOGGER.error("ARIS: 路線ファイルの再読込に失敗しました", e);
            return false;
        }
    }

    public static LineConfig getConfig() {
        return CONFIG;
    }

    public static void setConfig(LineConfig cfg) {
        CONFIG = cfg;
    }
}
