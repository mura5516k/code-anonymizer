package com.mura.codeanonymizer.core.mapping;

public class MappingEntry {
    private String original;
    private String replacement;
    private NameKind kind;

    public MappingEntry() {
    }

    public MappingEntry(String original, String replacement, NameKind kind) {
        this.original = original;
        this.replacement = replacement;
        this.kind = kind;
    }

    public String getOriginal() {
        return original;
    }

    public void setOriginal(String original) {
        this.original = original;
    }

    public String getReplacement() {
        return replacement;
    }

    public void setReplacement(String replacement) {
        this.replacement = replacement;
    }

    public NameKind getKind() {
        return kind;
    }

    public void setKind(NameKind kind) {
        this.kind = kind;
    }
}
