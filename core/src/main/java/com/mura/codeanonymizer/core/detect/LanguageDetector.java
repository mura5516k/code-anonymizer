package com.mura.codeanonymizer.core.detect;

import java.util.regex.Pattern;

/**
 * 入力コードがJavaかSQLかを機械的に判定する。判定不能な場合はUNKNOWNを返し、
 * 呼び出し側(GUI)でユーザーに選択させる。
 */
public final class LanguageDetector {

    private static final Pattern SQL_LEADING_KEYWORD = Pattern.compile(
            "^(SELECT|INSERT|UPDATE|DELETE|WITH)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern JAVA_HINT = Pattern.compile(
            "\\b(package|import|class|interface|enum|void|return|new|public|private|protected|static|final)\\b"
            + "|@\\w+");

    private LanguageDetector() {
    }

    public static DetectedLanguage detect(String source) {
        String trimmed = source == null ? "" : source.trim();
        if (trimmed.isEmpty()) {
            return DetectedLanguage.UNKNOWN;
        }
        if (SQL_LEADING_KEYWORD.matcher(trimmed).find()) {
            return DetectedLanguage.SQL;
        }
        if (JAVA_HINT.matcher(trimmed).find()) {
            return DetectedLanguage.JAVA;
        }
        return DetectedLanguage.UNKNOWN;
    }
}
