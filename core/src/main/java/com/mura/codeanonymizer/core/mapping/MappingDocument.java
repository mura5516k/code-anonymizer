package com.mura.codeanonymizer.core.mapping;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class MappingDocument {
    private int version = 1;
    private Map<NameKind, Integer> counters = new EnumMap<>(NameKind.class);
    private List<MappingEntry> entries = new ArrayList<>();

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Map<NameKind, Integer> getCounters() {
        return counters;
    }

    public void setCounters(Map<NameKind, Integer> counters) {
        this.counters = counters;
    }

    public List<MappingEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<MappingEntry> entries) {
        this.entries = entries;
    }
}
