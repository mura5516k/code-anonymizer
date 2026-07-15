package com.mura.codeanonymizer.core.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 元の識別子/テーブル/カラム名などを機械的な名前に決定的にマッピングし、
 * ローカルの mapping.json にのみ永続化する。
 */
public class MappingStore {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path path;
    private MappingDocument document;
    private final Map<String, String> lookup = new HashMap<>();

    public MappingStore(Path path) {
        this.path = path;
        this.document = load(path);
        rebuildLookup();
    }

    public static Path defaultMappingPath() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Paths.get(appData, "code-anonymizer", "mapping.json");
        }
        return Paths.get(System.getProperty("user.home"), ".code-anonymizer", "mapping.json");
    }

    private static MappingDocument load(Path path) {
        if (Files.exists(path)) {
            try {
                return MAPPER.readValue(path.toFile(), MappingDocument.class);
            } catch (IOException e) {
                throw new MappingException("マッピングファイルの読み込みに失敗しました: " + path, e);
            }
        }
        return new MappingDocument();
    }

    private void rebuildLookup() {
        lookup.clear();
        for (MappingEntry entry : document.getEntries()) {
            lookup.put(key(entry.getKind(), entry.getOriginal()), entry.getReplacement());
        }
    }

    private static String key(NameKind kind, String original) {
        return kind.name() + "::" + original;
    }

    public Path getPath() {
        return path;
    }

    public MappingDocument getDocument() {
        return document;
    }

    /**
     * 元の名前に対する置換名を返す。既知であれば既存のものを返し、
     * 未知であれば種別ごとの連番を採番して新規登録する。
     */
    public synchronized String getOrCreate(String original, NameKind kind) {
        String existing = lookup.get(key(kind, original));
        if (existing != null) {
            return existing;
        }
        int next = document.getCounters().getOrDefault(kind, 1);
        String replacement = kind.format(next);
        document.getCounters().put(kind, next + 1);
        document.getEntries().add(new MappingEntry(original, replacement, kind));
        lookup.put(key(kind, original), replacement);
        return replacement;
    }

    public synchronized void save() {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            MAPPER.writeValue(path.toFile(), document);
        } catch (IOException e) {
            throw new MappingException("マッピングファイルの保存に失敗しました: " + path, e);
        }
    }

    public static MappingStore createNew(Path path) {
        try {
            if (Files.exists(path)) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new MappingException("既存マッピングファイルの削除に失敗しました: " + path, e);
        }
        MappingStore store = new MappingStore(path);
        store.save();
        return store;
    }
}
