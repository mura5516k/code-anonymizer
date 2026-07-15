package com.mura.codeanonymizer.ui;

import com.mura.codeanonymizer.core.mapping.MappingEntry;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class MappingTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"元名", "置換名", "種別"};

    private List<MappingEntry> entries = new ArrayList<>();

    public void setEntries(List<MappingEntry> entries) {
        this.entries = new ArrayList<>(entries);
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return entries.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        MappingEntry entry = entries.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> entry.getOriginal();
            case 1 -> entry.getReplacement();
            case 2 -> entry.getKind().name();
            default -> "";
        };
    }
}
