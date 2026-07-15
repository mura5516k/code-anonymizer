package com.mura.codeanonymizer.core.sql;

import com.mura.codeanonymizer.core.mapping.MappingEntry;
import com.mura.codeanonymizer.core.mapping.MappingStore;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQLのパースに失敗した場合のフォールバック。
 * 既存マッピングのエントリを使い、単語境界で単純置換する。
 * 部分一致事故を防ぐため、元の名前が長い順に処理する。
 */
final class SqlFallbackReplacer {

    private SqlFallbackReplacer() {
    }

    static String replace(String sql, MappingStore store) {
        List<MappingEntry> entries = store.getDocument().getEntries().stream()
                .sorted(Comparator.comparingInt((MappingEntry e) -> e.getOriginal().length()).reversed())
                .toList();

        String result = sql;
        for (MappingEntry entry : entries) {
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(entry.getOriginal()) + "\\b");
            Matcher matcher = pattern.matcher(result);
            result = matcher.replaceAll(Matcher.quoteReplacement(entry.getReplacement()));
        }
        return result;
    }
}
