package com.mura.codeanonymizer.core.sql;

import com.mura.codeanonymizer.core.mapping.MappingStore;
import com.mura.codeanonymizer.core.mapping.NameKind;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

import java.util.Map;

/**
 * 式ツリー中に出現するカラム参照(t1.col等)をリネームする。
 * テーブル修飾子はcollectFromItemで収集済みのテーブル名/エイリアス名マップを参照して解決する。
 */
class ColumnRenamer extends ExpressionVisitorAdapter {

    private final MappingStore store;
    private final Map<String, String> tableRenames;
    private final Map<String, String> aliasRenames;

    ColumnRenamer(MappingStore store, Map<String, String> tableRenames, Map<String, String> aliasRenames) {
        this.store = store;
        this.tableRenames = tableRenames;
        this.aliasRenames = aliasRenames;
    }

    @Override
    public void visit(Column column) {
        String newColumnName = store.getOrCreate(column.getColumnName(), NameKind.COLUMN);
        column.setColumnName(newColumnName);

        Table qualifier = column.getTable();
        if (qualifier != null && qualifier.getName() != null && !qualifier.getName().isEmpty()) {
            String oldQualifier = qualifier.getName();
            String newQualifier = aliasRenames.get(oldQualifier);
            if (newQualifier == null) {
                newQualifier = tableRenames.get(oldQualifier);
            }
            if (newQualifier == null) {
                newQualifier = store.getOrCreate(oldQualifier, NameKind.ALIAS);
            }
            qualifier.setName(newQualifier);
        }
    }
}
