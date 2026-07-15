package com.mura.codeanonymizer.ui;

import com.mura.codeanonymizer.core.mapping.MappingStore;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class MainApp {

    private MainApp() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // ルック&フィール設定に失敗しても続行する
            }
            MappingStore store = new MappingStore(MappingStore.defaultMappingPath());
            MainFrame frame = new MainFrame(store);
            frame.setVisible(true);
        });
    }
}
