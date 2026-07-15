package com.mura.codeanonymizer.core.mapping;

public enum NameKind {
    CLASS {
        @Override
        public String format(int n) {
            return String.format("ClassA%03d", n);
        }
    },
    METHOD {
        @Override
        public String format(int n) {
            return String.format("methodM%03d", n);
        }
    },
    VARIABLE {
        @Override
        public String format(int n) {
            return String.format("varV%03d", n);
        }
    },
    PACKAGE {
        @Override
        public String format(int n) {
            return String.format("com.anon.pkg%03d", n);
        }
    },
    TABLE {
        @Override
        public String format(int n) {
            return String.format("tbl_a%03d", n);
        }
    },
    COLUMN {
        @Override
        public String format(int n) {
            return String.format("col_c%03d", n);
        }
    },
    ALIAS {
        @Override
        public String format(int n) {
            return "t" + n;
        }
    },
    STRING {
        @Override
        public String format(int n) {
            return String.format("STR_%03d", n);
        }
    };

    public abstract String format(int n);
}
