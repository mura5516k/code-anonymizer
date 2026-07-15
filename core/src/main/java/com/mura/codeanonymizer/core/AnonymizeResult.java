package com.mura.codeanonymizer.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AnonymizeResult {
    private final String output;
    private final List<String> warnings;

    public AnonymizeResult(String output, List<String> warnings) {
        this.output = output;
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    public String getOutput() {
        return output;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}
