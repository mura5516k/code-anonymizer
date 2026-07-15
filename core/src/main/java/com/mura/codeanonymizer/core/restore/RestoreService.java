package com.mura.codeanonymizer.core.restore;

import com.mura.codeanonymizer.core.mapping.MappingEntry;
import com.mura.codeanonymizer.core.mapping.MappingStore;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AIの回答に含まれる置換名を元の名前に復元する。
 * varV001 と varV0011 のような接頭辞衝突を避けるため、
 * 置換名が長い順に単語境界で置換する。
 */
public class RestoreService {

    private final MappingStore store;

    public RestoreService(MappingStore store) {
        this.store = store;
    }

    public String restore(String aiAnswer) {
        List<MappingEntry> entries = store.getDocument().getEntries().stream()
                .sorted(Comparator.comparingInt((MappingEntry e) -> e.getReplacement().length()).reversed())
                .toList();

        String result = aiAnswer;
        for (MappingEntry entry : entries) {
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(entry.getReplacement()) + "\\b");
            Matcher matcher = pattern.matcher(result);
            result = matcher.replaceAll(Matcher.quoteReplacement(entry.getOriginal()));
        }
        return result;
    }
}
