package com.mura.codeanonymizer.ui;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.Dimension;

/**
 * 例外メッセージ表示用の共通ダイアログ。
 * JOptionPaneに長文をそのまま渡すと画面いっぱいに広がるため、
 * 固定サイズのスクロール領域に収めて表示する。
 */
final class ErrorDialogs {

    private static final int MAX_MESSAGE_LENGTH = 600;

    private ErrorDialogs() {
    }

    static void show(Component parent, String title, Throwable ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        if (message.length() > MAX_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_MESSAGE_LENGTH) + " …(以下省略)";
        }

        JTextArea area = new JTextArea(message);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBackground(UIManager.getColor("Panel.background"));
        area.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(area);
        scrollPane.setBorder(null);
        scrollPane.setPreferredSize(new Dimension(480, 140));

        JOptionPane.showMessageDialog(parent, scrollPane, title, JOptionPane.ERROR_MESSAGE);
    }
}
