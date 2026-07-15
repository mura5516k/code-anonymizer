package com.mura.codeanonymizer.ui;

import com.mura.codeanonymizer.core.AnonymizeOptions;
import com.mura.codeanonymizer.core.AnonymizeResult;
import com.mura.codeanonymizer.core.detect.DetectedLanguage;
import com.mura.codeanonymizer.core.detect.LanguageDetector;
import com.mura.codeanonymizer.core.java_.JavaAnonymizer;
import com.mura.codeanonymizer.core.sql.SqlAnonymizer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.Font;
import java.awt.FlowLayout;

import java.util.List;

public class AnonymizeTab extends JPanel {

    private static final String MODE_AUTO = "自動判定";
    private static final String MODE_JAVA = "Java";
    private static final String MODE_SQL = "SQL";

    private final MainFrame mainFrame;
    private final JComboBox<String> modeCombo = new JComboBox<>(new String[]{MODE_AUTO, MODE_JAVA, MODE_SQL});
    private final JCheckBox removeCommentsCheck = new JCheckBox("コメントを削除", true);
    private final JCheckBox maskStringsCheck = new JCheckBox("文字列リテラルをマスク", false);
    private final JTextArea inputArea = new JTextArea();
    private final JTextArea outputArea = new JTextArea();

    public AnonymizeTab(MainFrame mainFrame) {
        super(new BorderLayout(8, 8));
        this.mainFrame = mainFrame;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        Font monospaced = new Font(Font.MONOSPACED, Font.PLAIN, 13);
        inputArea.setFont(monospaced);
        outputArea.setFont(monospaced);
        outputArea.setEditable(false);

        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        optionsPanel.add(new javax.swing.JLabel("モード:"));
        optionsPanel.add(modeCombo);
        optionsPanel.add(removeCommentsCheck);
        optionsPanel.add(maskStringsCheck);
        add(optionsPanel, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(inputArea), new JScrollPane(outputArea));
        splitPane.setResizeWeight(0.5);
        add(splitPane, BorderLayout.CENTER);

        JButton runButton = new JButton("匿名化実行");
        runButton.addActionListener(e -> onRun());
        JButton copyButton = new JButton("結果をコピー");
        copyButton.addActionListener(e -> onCopy());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(runButton);
        buttonPanel.add(copyButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void onRun() {
        String source = inputArea.getText();
        if (source.isBlank()) {
            mainFrame.setStatus("入力が空です。");
            return;
        }
        if (source.trim().startsWith("<")) {
            mainFrame.setStatus("XML等の非対応形式のため処理を中止しました。");
            JOptionPane.showMessageDialog(this,
                    "入力がXML等の非対応形式のようです。\nこのツールはJavaソースコードとSQLのみ対応しています。",
                    "非対応の形式", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String mode = (String) modeCombo.getSelectedItem();
        String effectiveMode = mode;
        if (MODE_AUTO.equals(mode)) {
            DetectedLanguage detected = LanguageDetector.detect(source);
            effectiveMode = switch (detected) {
                case JAVA -> MODE_JAVA;
                case SQL -> MODE_SQL;
                case UNKNOWN -> askUserForMode();
            };
            if (effectiveMode == null) {
                mainFrame.setStatus("モードが選択されなかったため処理を中止しました。");
                return;
            }
        }

        AnonymizeOptions options = new AnonymizeOptions(removeCommentsCheck.isSelected(), maskStringsCheck.isSelected());

        try {
            AnonymizeResult result;
            if (MODE_JAVA.equals(effectiveMode)) {
                result = new JavaAnonymizer(mainFrame.getStore()).anonymize(source, options);
            } else {
                result = new SqlAnonymizer(mainFrame.getStore()).anonymize(source, options);
            }
            mainFrame.getStore().save();
            outputArea.setText(result.getOutput());
            mainFrame.refreshMappingTable();

            List<String> warnings = result.getWarnings();
            if (warnings.isEmpty()) {
                mainFrame.setStatus("匿名化が完了しました (" + effectiveMode + ")。");
            } else {
                mainFrame.setStatus("匿名化が完了しました (" + effectiveMode + ")。警告: " + String.join(" / ", warnings));
            }
        } catch (RuntimeException ex) {
            ErrorDialogs.show(this, "匿名化エラー", ex);
            mainFrame.setStatus("エラーが発生しました。");
        }
    }

    private String askUserForMode() {
        Object[] choices = {MODE_JAVA, MODE_SQL};
        int choice = JOptionPane.showOptionDialog(this,
                "入力コードの種類を自動判定できませんでした。種類を選択してください。",
                "モード選択",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                choices,
                choices[0]);
        if (choice == JOptionPane.CLOSED_OPTION || choice < 0) {
            return null;
        }
        return (String) choices[choice];
    }

    private void onCopy() {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(outputArea.getText()), null);
        mainFrame.setStatus("結果をクリップボードにコピーしました。");
    }
}
