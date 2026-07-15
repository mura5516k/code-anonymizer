package com.mura.codeanonymizer.core;

public class AnonymizeOptions {
    private final boolean removeComments;
    private final boolean maskStrings;

    public AnonymizeOptions(boolean removeComments, boolean maskStrings) {
        this.removeComments = removeComments;
        this.maskStrings = maskStrings;
    }

    public boolean isRemoveComments() {
        return removeComments;
    }

    public boolean isMaskStrings() {
        return maskStrings;
    }
}
