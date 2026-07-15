package com.mura.codeanonymizer.core.java_;

import com.mura.codeanonymizer.core.AnonymizeOptions;
import com.mura.codeanonymizer.core.AnonymizeResult;
import com.mura.codeanonymizer.core.mapping.MappingStore;
import com.mura.codeanonymizer.core.restore.RestoreService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaAnonymizerTest {

    private static final String SOURCE = """
            package com.example.myapp;

            import java.util.List;
            import java.util.ArrayList;
            import com.example.myapp.util.Helper;
            import org.springframework.stereotype.Service;
            import lombok.Data;

            @Service
            @Data
            public class OrderService {
                private List<String> orderNames;

                public String buildOrderLabel(String orderId) {
                    List<String> names = new ArrayList<>();
                    names.add(orderId);
                    return this.orderNames.toString() + names.toString();
                }
            }
            """;

    @Test
    void declarationsAndUsagesRenamedConsistently_denylistUnchanged(@TempDir Path tempDir) {
        MappingStore store = new MappingStore(tempDir.resolve("mapping.json"));
        JavaAnonymizer anonymizer = new JavaAnonymizer(store);

        AnonymizeResult result = anonymizer.anonymize(SOURCE, new AnonymizeOptions(false, false));
        String output = result.getOutput();

        assertTrue(output.contains("String"), "java.lang.String はデナイリストにより変更されない");
        assertTrue(output.contains("List<"), "java.util.List はデナイリストにより変更されない");
        assertTrue(output.contains("@Service"), "Spring の @Service はデナイリストにより変更されない");
        assertTrue(output.contains("@Data"), "Lombok の @Data はデナイリストにより変更されない");

        assertTrue(!output.contains("OrderService"), "クラス名はリネームされる");
        assertTrue(!output.contains("buildOrderLabel"), "メソッド名はリネームされる");
        assertTrue(!output.contains("orderNames"), "フィールド名はリネームされる");
        assertTrue(!output.contains("orderId"), "パラメータ名はリネームされる");

        String className = extractClassNameToken(output);
        long occurrences = countOccurrences(output, className);
        assertTrue(occurrences >= 1);
    }

    @Test
    void anonymizeThenRestoreRoundTripsAllIdentifiers(@TempDir Path tempDir) {
        MappingStore store = new MappingStore(tempDir.resolve("mapping.json"));
        JavaAnonymizer anonymizer = new JavaAnonymizer(store);

        AnonymizeResult anonymized = anonymizer.anonymize(SOURCE, new AnonymizeOptions(false, false));
        store.save();

        RestoreService restoreService = new RestoreService(store);
        String restored = restoreService.restore(anonymized.getOutput());

        assertTrue(restored.contains("OrderService"));
        assertTrue(restored.contains("buildOrderLabel"));
        assertTrue(restored.contains("orderNames"));
        assertTrue(restored.contains("orderId"));
        assertTrue(restored.contains("package com.example.myapp;"));
    }

    @Test
    void internalImportsAreFullyAnonymized_libraryImportsUntouched(@TempDir Path tempDir) {
        MappingStore store = new MappingStore(tempDir.resolve("mapping.json"));
        JavaAnonymizer anonymizer = new JavaAnonymizer(store);

        String source = """
                package com.example.myapp;

                import java.util.List;
                import com.example.myapp.util.Helper;
                import com.example.other.OtherService;
                import static com.example.other.Constants.MAX_RETRY;
                import org.springframework.stereotype.Service;

                @Service
                public class OrderService {
                    private OtherService otherService;

                    public int retry() {
                        Helper helper = new Helper();
                        return MAX_RETRY;
                    }
                }
                """;

        AnonymizeResult result = anonymizer.anonymize(source, new AnonymizeOptions(false, false));
        String output = result.getOutput();

        assertTrue(!output.contains("com.example"), "社内パッケージ名はimport文からも消える");
        assertTrue(!output.contains("Helper"), "importされたクラス名は本文含めリネームされる");
        assertTrue(!output.contains("OtherService"), "別パッケージの社内クラス名もリネームされる");
        assertTrue(!output.contains("MAX_RETRY"), "staticインポートのメンバ名もリネームされる");
        assertTrue(!output.contains("myapp"), "社内パッケージの末尾セグメントも残らない (java.utilは対象外)");

        assertTrue(output.contains("import java.util.List;"), "JDKのimportは変更されない");
        assertTrue(output.contains("import org.springframework.stereotype.Service;"), "Springのimportは変更されない");

        // ラウンドトリップ確認
        store.save();
        RestoreService restoreService = new RestoreService(store);
        String restored = restoreService.restore(output);
        assertTrue(restored.contains("import com.example.myapp.util.Helper;"));
        assertTrue(restored.contains("import com.example.other.OtherService;"));
        assertTrue(restored.contains("MAX_RETRY"));
    }

    @Test
    void enumConstantsMethodRefsAndTextBlocksAreAnonymized(@TempDir Path tempDir) {
        MappingStore store = new MappingStore(tempDir.resolve("mapping.json"));
        JavaAnonymizer anonymizer = new JavaAnonymizer(store);

        String source = """
                package com.example.batch;

                public class UriConfig {
                    public enum JobStatus { CHOHYO_WAIT, SYNC_DONE }

                    public java.util.function.Supplier<String> ref() {
                        return UriConfig::buildLabel;
                    }

                    static String buildLabel() {
                        return \"""
                            SELECT syain_cd FROM shain_master
                            \""";
                    }
                }
                """;

        AnonymizeResult result = anonymizer.anonymize(source, new AnonymizeOptions(true, true));
        String output = result.getOutput();

        assertTrue(!output.contains("CHOHYO_WAIT"), "enum定数はリネームされる");
        assertTrue(!output.contains("SYNC_DONE"), "enum定数はリネームされる");
        assertTrue(!output.contains("buildLabel"), "メソッド参照(::)の右辺もリネームされる");
        assertTrue(!output.contains("shain_master"), "テキストブロックもマスクされる");
        assertTrue(result.getWarnings().isEmpty(), "誤検知の警告が出ない: " + result.getWarnings());
    }

    @Test
    void warnsWhenStringLiteralsPresentAndMaskDisabled(@TempDir Path tempDir) {
        MappingStore store = new MappingStore(tempDir.resolve("mapping.json"));
        JavaAnonymizer anonymizer = new JavaAnonymizer(store);

        String source = """
                package com.example.batch;

                public class UriConfig {
                    private static final String[] uriList = { "/a/login", "/b/initsearch" };
                }
                """;

        AnonymizeResult result = anonymizer.anonymize(source, new AnonymizeOptions(true, false));

        assertTrue(result.getOutput().contains("/a/login"), "マスクOFFなら文字列は残る(仕様どおり)");
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("文字列リテラル")),
                "マスクOFFで文字列がある場合は警告が出る");
    }

    @Test
    void warnsAboutSamePackageTypesThatCannotBeAnonymized(@TempDir Path tempDir) {
        MappingStore store = new MappingStore(tempDir.resolve("mapping.json"));
        JavaAnonymizer anonymizer = new JavaAnonymizer(store);

        String source = """
                package com.example.batch;

                public class OrderJob {
                    private OrderRepository repository;
                }
                """;

        AnonymizeResult result = anonymizer.anonymize(source, new AnonymizeOptions(true, false));

        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("OrderRepository")),
                "同一パッケージ参照とみられる未解決型は警告される: " + result.getWarnings());
    }

    private static String extractClassNameToken(String output) {
        int idx = output.indexOf("public class ");
        String rest = output.substring(idx + "public class ".length());
        return rest.split("[\\s{]", 2)[0].trim();
    }

    private static long countOccurrences(String haystack, String needle) {
        long count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
