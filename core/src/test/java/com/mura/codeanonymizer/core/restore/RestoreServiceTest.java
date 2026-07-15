package com.mura.codeanonymizer.core.restore;

import com.mura.codeanonymizer.core.mapping.MappingEntry;
import com.mura.codeanonymizer.core.mapping.MappingStore;
import com.mura.codeanonymizer.core.mapping.NameKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RestoreServiceTest {

    @Test
    void restoreDoesNotMisreplaceWhenOneReplacementIsPrefixOfAnother(@TempDir Path tempDir) {
        MappingStore store = new MappingStore(tempDir.resolve("mapping.json"));

        // varV001 が varV0011 の接頭辞になっている人工的なケース
        store.getDocument().getEntries().add(new MappingEntry("firstVar", "varV001", NameKind.VARIABLE));
        store.getDocument().getEntries().add(new MappingEntry("eleventhVar", "varV0011", NameKind.VARIABLE));

        RestoreService restoreService = new RestoreService(store);

        String aiAnswer = "値は varV0011 です。もう一方は varV001 です。";
        String restored = restoreService.restore(aiAnswer);

        assertEquals("値は eleventhVar です。もう一方は firstVar です。", restored);
    }
}
