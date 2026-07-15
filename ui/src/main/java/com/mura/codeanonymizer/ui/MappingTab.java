package com.mura.codeanonymizer.ui;

import com.mura.codeanonymizer.core.mapping.MappingStore;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.io.File;
import java.nio.file.Path;

public class MappingTab extends JPanel {

    private final MainFrame mainFrame;
    private final JLabel pathLabel = new JLabel();
    private final MappingTableModel tableModel = new MappingTableModel();
    private final JTable table = new JTable(tableModel);

    public MappingTab(MainFrame mainFrame) {
        super(new BorderLayout(8, 8));
        this.mainFrame = mainFrame;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel topPanel = new JPanel(new BorderLayout(4, 4));
        topPanel.add(pathLabel, BorderLayout.NORTH);

        JLabel warningLabel = new JLabel(
                "警告: このファイルは元の名前を含む機密ファイルであり社外に出さないこと");
        warningLabel.setForeground(Color.RED);
        topPanel.add(warningLabel, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JButton switchButton = new JButton("マッピングファイル切替");
        switchButton.addActionListener(e -> onSwitchMappingFile());

        JButton newButton = new JButton("新規マッピング作成");
        newButton.addActionListener(e -> onCreateNewMapping());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(switchButton);
        buttonPanel.add(newButton);
        add(buttonPanel, BorderLayout.SOUTH);

        refresh();
    }

    public void refresh() {
        MappingStore store = mainFrame.getStore();
        pathLabel.setText("現在のマッピングファイル: " + store.getPath());
        tableModel.setEntries(store.getDocument().getEntries());
    }

    private void onSwitchMappingFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("マッピングファイルを選択");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("mapping.json", "json"));
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path selected = chooser.getSelectedFile().toPath();
        try {
            MappingStore newStore = new MappingStore(selected);
            mainFrame.setStore(newStore);
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "エラー", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onCreateNewMapping() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("新規マッピングファイルの保存先を選択");
        chooser.setSelectedFile(new File("mapping.json"));
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path selected = chooser.getSelectedFile().toPath();
        try {
            MappingStore newStore = MappingStore.createNew(selected);
            mainFrame.setStore(newStore);
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "エラー", JOptionPane.ERROR_MESSAGE);
        }
    }
}
