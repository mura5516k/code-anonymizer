package com.mura.codeanonymizer.core.sql;

/**
 * 文字列リテラル内の "--" や "/*" を誤って除去しないよう、
 * シングルクォート文字列を認識しながらSQLコメントを除去する。
 */
final class SqlCommentStripper {

    private SqlCommentStripper() {
    }

    static String strip(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        boolean inString = false;
        int i = 0;
        int len = sql.length();
        while (i < len) {
            char c = sql.charAt(i);
            if (inString) {
                out.append(c);
                if (c == '\'') {
                    if (i + 1 < len && sql.charAt(i + 1) == '\'') {
                        out.append(sql.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    inString = false;
                }
                i++;
                continue;
            }
            if (c == '\'') {
                inString = true;
                out.append(c);
                i++;
                continue;
            }
            if (c == '-' && i + 1 < len && sql.charAt(i + 1) == '-') {
                while (i < len && sql.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < len && sql.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < len && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) {
                    i++;
                }
                i += 2;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }
}
