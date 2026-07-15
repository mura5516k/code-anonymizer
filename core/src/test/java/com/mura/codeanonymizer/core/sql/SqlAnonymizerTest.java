package com.mura.codeanonymizer.core.sql;

import com.mura.codeanonymizer.core.AnonymizeOptions;
import com.mura.codeanonymizer.core.AnonymizeResult;
import com.mura.codeanonymizer.core.mapping.MappingStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlAnonymizerTest {

    @Test
    void selectWithJoinRenamesTablesColumnsAndAliases(@TempDir Path tempDir) {
        MappingStore store = new MappingStore(tempDir.resolve("mapping.json"));
        SqlAnonymizer anonymizer = new SqlAnonymizer(store);

        String sql = "SELECT o.order_id, c.customer_name FROM orders o "
                + "JOIN customers c ON o.customer_id = c.customer_id "
                + "WHERE o.status = 'PAID'";

        AnonymizeResult result = anonymizer.anonymize(sql, new AnonymizeOptions(false, false));
        String output = result.getOutput();

        assertFalse(output.contains("orders"));
        assertFalse(output.contains("customers"));
        assertFalse(output.contains("order_id"));
        assertFalse(output.contains("customer_name"));
        assertFalse(output.contains("customer_id"));
        assertTrue(output.contains("SELECT"));
        assertTrue(output.contains("JOIN"));
        assertTrue(output.contains("'PAID'"), "文字列リテラルはマスクオプションOFF時は変更されない");
    }

    @Test
    void insertStatementRenamesTableAndColumns(@TempDir Path tempDir) {
        MappingStore store = new MappingStore(tempDir.resolve("mapping.json"));
        SqlAnonymizer anonymizer = new SqlAnonymizer(store);

        String sql = "INSERT INTO users (id, name) VALUES (1, 'Alice')";
        AnonymizeResult result = anonymizer.anonymize(sql, new AnonymizeOptions(false, false));
        String output = result.getOutput();

        assertFalse(containsWord(output, "users"));
        assertFalse(containsWord(output, "id"));
        assertFalse(containsWord(output, "name"));
        assertTrue(output.contains("INSERT INTO"));
    }

    @Test
    void updateStatementRenamesTableAndColumns(@TempDir Path tempDir) {
        MappingStore store = new MappingStore(tempDir.resolve("mapping.json"));
        SqlAnonymizer anonymizer = new SqlAnonymizer(store);

        String sql = "UPDATE accounts SET balance = balance + 100 WHERE account_id = 1";
        AnonymizeResult result = anonymizer.anonymize(sql, new AnonymizeOptions(false, false));
        String output = result.getOutput();

        assertFalse(output.contains("accounts"));
        assertFalse(output.contains("balance"));
        assertFalse(output.contains("account_id"));
        assertTrue(output.contains("UPDATE"));
        assertTrue(output.contains("SET"));
    }

    @Test
    void unparsableSqlFallsBackToMappingBasedReplacement(@TempDir Path tempDir) {
        MappingStore store = new MappingStore(tempDir.resolve("mapping.json"));
        SqlAnonymizer anonymizer = new SqlAnonymizer(store);

        anonymizer.anonymize("SELECT id FROM orders", new AnonymizeOptions(false, false));
        store.save();

        String brokenSql = "SELECT id FROM orders WHERE ((( unbalanced";
        AnonymizeResult result = anonymizer.anonymize(brokenSql, new AnonymizeOptions(false, false));

        assertFalse(result.getWarnings().isEmpty(), "フォールバック時は警告が出る");
        assertFalse(result.getOutput().contains("orders"), "既存マッピングにより orders は置換される");
    }

    @Test
    void sameInputAnonymizedTwiceProducesIdenticalOutput(@TempDir Path tempDir) {
        MappingStore store = new MappingStore(tempDir.resolve("mapping.json"));
        SqlAnonymizer anonymizer = new SqlAnonymizer(store);

        String sql = "SELECT o.id FROM orders o WHERE o.status = 'PAID'";
        String first = anonymizer.anonymize(sql, new AnonymizeOptions(false, false)).getOutput();
        String second = anonymizer.anonymize(sql, new AnonymizeOptions(false, false)).getOutput();

        assertTrue(first.equals(second), "同じ入力を2回anonymizeすると同一出力になる");
    }

    private static boolean containsWord(String text, String word) {
        return java.util.regex.Pattern.compile("\\b" + word + "\\b").matcher(text).find();
    }
}
