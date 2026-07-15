package com.mura.codeanonymizer.ui;

import com.mura.codeanonymizer.core.restore.RestoreService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

public class RestoreTab extends JPanel {

    private final MainFrame mainFrame;
    private final JTextArea inputArea = new JTextArea();
    private final JTextArea outputArea = new JTextArea();

    public RestoreTab(MainFrame mainFrame) {
        super(new BorderLayout(8, 8));
        this.mainFrame = mainFrame;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        Font monospaced = new Font(Font.MONOSPACED, Font.PLAIN, 13);
        inputArea.setFont(monospaced);
        outputArea.setFont(monospaced);
        outputArea.setEditable(false);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(inputArea), new JScrollPane(outputArea));
        splitPane.setResizeWeight(0.5);
        add(splitPane, BorderLayout.CENTER);

        JButton runButton = new JButton("復元実行");
        runButton.addActionListener(e -> onRun());
        JButton copyButton = new JButton("結果をコピー");
        copyButton.addActionListener(e -> onCopy());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(runButton);
        buttonPanel.add(copyButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void onRun() {
        String input = inputArea.getText();
        if (input.isBlank()) {
            mainFrame.setStatus("入力が空です。");
            return;
        }
        try {
            String restored = new RestoreService(mainFrame.getStore()).restore(input);
            outputArea.setText(restored);
            mainFrame.setStatus("復元が完了しました。");
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "復元エラー", JOptionPane.ERROR_MESSAGE);
            mainFrame.setStatus("エラーが発生しました。");
        }
    }

    private void onCopy() {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(outputArea.getText()), null);
        mainFrame.setStatus("結果をクリップボードにコピーしました。");
    }
}
