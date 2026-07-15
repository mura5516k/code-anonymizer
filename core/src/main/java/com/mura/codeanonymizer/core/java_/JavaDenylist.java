package com.mura.codeanonymizer.core.java_;

import java.util.Set;

/**
 * リネーム対象から除外する名前の集合。
 * Java標準ライブラリの主要クラス、Spring/MyBatis/Lombokの主要アノテーション、
 * 共通のオーバーライドメソッド名を含む。
 */
public final class JavaDenylist {

    public static final Set<String> NAMES = Set.of(
            // java.lang / java.util 主要クラス
            "String", "Object", "Integer", "Long", "Double", "Float", "Boolean",
            "Character", "Byte", "Short", "Void", "Number", "Math", "System",
            "Thread", "Runnable", "Class", "Exception", "RuntimeException",
            "Throwable", "Error", "StringBuilder", "StringBuffer", "Comparable",
            "Iterable", "AutoCloseable", "CharSequence", "Enum", "Record",
            "IllegalArgumentException", "IllegalStateException", "NullPointerException",
            "UnsupportedOperationException", "IndexOutOfBoundsException",
            "InterruptedException", "ClassCastException", "NumberFormatException",
            "List", "ArrayList", "LinkedList", "Map", "HashMap", "LinkedHashMap",
            "TreeMap", "Set", "HashSet", "LinkedHashSet", "TreeSet", "Collection",
            "Collections", "Arrays", "Optional", "Stream", "Comparator", "Iterator",
            "Objects", "UUID", "Pattern", "Matcher",

            // Java標準アノテーション
            "Override", "Deprecated", "SuppressWarnings", "FunctionalInterface", "SafeVarargs",

            // Spring
            "Autowired", "Service", "Repository", "Controller", "RestController",
            "Transactional", "Component", "Bean", "Configuration", "RequestMapping",
            "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping",
            "Value", "Qualifier", "RequestParam", "RequestBody", "PathVariable",
            "ResponseBody", "ComponentScan", "SpringBootApplication",

            // MyBatis
            "Mapper", "Select", "Insert", "Update", "Delete", "Param", "Results", "Result",

            // Lombok
            "Data", "Builder", "Getter", "Setter", "AllArgsConstructor", "NoArgsConstructor",
            "RequiredArgsConstructor", "Slf4j", "EqualsAndHashCode", "ToString",

            // 共通メソッド名
            "main", "toString", "equals", "hashCode", "compareTo", "run", "close"
    );

    /**
     * import文をリネーム対象から除外する既知ライブラリのパッケージプレフィックス。
     * ここに該当しないimportは社内コードとみなして匿名化する。
     */
    public static final java.util.List<String> LIBRARY_PACKAGE_PREFIXES = java.util.List.of(
            "java.", "javax.", "jakarta.", "lombok.",
            "org.springframework.", "org.apache.", "org.slf4j.", "org.junit.",
            "org.mybatis.", "org.hibernate.", "org.mockito.", "org.assertj.",
            "com.fasterxml.", "com.google.", "io.micrometer.", "io.swagger.",
            "org.w3c.", "org.xml.", "org.yaml.", "ch.qos.logback."
    );

    private JavaDenylist() {
    }

    public static boolean contains(String name) {
        return NAMES.contains(name);
    }

    public static boolean isLibraryPackage(String qualifiedName) {
        for (String prefix : LIBRARY_PACKAGE_PREFIXES) {
            if (qualifiedName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
