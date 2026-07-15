package com.mura.codeanonymizer.core.sql;

import com.mura.codeanonymizer.core.AnonymizeOptions;
import com.mura.codeanonymizer.core.AnonymizeResult;
import com.mura.codeanonymizer.core.mapping.MappingStore;
import com.mura.codeanonymizer.core.mapping.NameKind;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.GroupByElement;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.Values;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SqlAnonymizer {

    private final MappingStore store;

    public SqlAnonymizer(MappingStore store) {
        this.store = store;
    }

    public AnonymizeResult anonymize(String source, AnonymizeOptions options) {
        List<String> warnings = new ArrayList<>();
        String input = options.isRemoveComments() ? SqlCommentStripper.strip(source) : source;

        try {
            Statement statement = CCJSqlParserUtil.parse(input);
            renameStatement(statement);
            return new AnonymizeResult(statement.toString(), warnings);
        } catch (JSQLParserException | RuntimeException e) {
            warnings.add("SQLのパースに失敗したため、既存マッピングによる単純置換にフォールバックしました: "
                    + e.getMessage());
            String fallback = SqlFallbackReplacer.replace(input, store);
            return new AnonymizeResult(fallback, warnings);
        }
    }

    private void renameStatement(Statement statement) {
        if (statement instanceof Insert insert) {
            renameInsert(insert);
        } else if (statement instanceof Update update) {
            renameUpdate(update);
        } else if (statement instanceof Delete delete) {
            renameDelete(delete);
        } else if (statement instanceof Select select) {
            renameSelect(select);
        }
    }

    private void renameSelect(Select select) {
        if (select instanceof PlainSelect plainSelect) {
            renamePlainSelect(plainSelect);
        } else if (select instanceof SetOperationList setOperationList) {
            for (Select s : setOperationList.getSelects()) {
                renameSelect(s);
            }
        } else if (select instanceof ParenthesedSelect parenthesedSelect) {
            renameSelect(parenthesedSelect.getSelect());
        }
    }

    private void renamePlainSelect(PlainSelect ps) {
        Map<String, String> tableRenames = new HashMap<>();
        Map<String, String> aliasRenames = new HashMap<>();

        if (ps.getFromItem() != null) {
            renameFromItem(ps.getFromItem(), tableRenames, aliasRenames);
        }
        if (ps.getJoins() != null) {
            for (Join join : ps.getJoins()) {
                renameFromItem(join.getRightItem(), tableRenames, aliasRenames);
            }
        }

        ColumnRenamer visitor = new ColumnRenamer(store, tableRenames, aliasRenames);

        if (ps.getSelectItems() != null) {
            for (SelectItem<?> item : ps.getSelectItems()) {
                Expression expr = item.getExpression();
                if (expr != null) {
                    expr.accept(visitor);
                }
            }
        }
        if (ps.getWhere() != null) {
            ps.getWhere().accept(visitor);
        }
        if (ps.getHaving() != null) {
            ps.getHaving().accept(visitor);
        }
        GroupByElement groupBy = ps.getGroupBy();
        if (groupBy != null && groupBy.getGroupByExpressions() != null) {
            for (Object e : groupBy.getGroupByExpressions()) {
                ((Expression) e).accept(visitor);
            }
        }
        if (ps.getOrderByElements() != null) {
            for (OrderByElement orderBy : ps.getOrderByElements()) {
                if (orderBy.getExpression() != null) {
                    orderBy.getExpression().accept(visitor);
                }
            }
        }
        if (ps.getJoins() != null) {
            for (Join join : ps.getJoins()) {
                if (join.getOnExpression() != null) {
                    join.getOnExpression().accept(visitor);
                }
            }
        }
    }

    private void renameFromItem(FromItem item, Map<String, String> tableRenames, Map<String, String> aliasRenames) {
        if (item instanceof Table table) {
            String newName = store.getOrCreate(table.getName(), NameKind.TABLE);
            tableRenames.put(table.getName(), newName);
            table.setName(newName);
            renameAlias(table.getAlias(), aliasRenames);
        } else if (item instanceof ParenthesedSelect parenthesedSelect) {
            renameSelect(parenthesedSelect.getSelect());
            renameAlias(parenthesedSelect.getAlias(), aliasRenames);
        }
    }

    private void renameAlias(Alias alias, Map<String, String> aliasRenames) {
        if (alias == null || alias.getName() == null || alias.getName().isEmpty()) {
            return;
        }
        String oldAlias = alias.getName();
        String newAlias = store.getOrCreate(oldAlias, NameKind.ALIAS);
        aliasRenames.put(oldAlias, newAlias);
        alias.setName(newAlias);
    }

    private void renameInsert(Insert insert) {
        Map<String, String> tableRenames = new HashMap<>();
        Map<String, String> aliasRenames = new HashMap<>();

        Table table = insert.getTable();
        if (table != null) {
            String newName = store.getOrCreate(table.getName(), NameKind.TABLE);
            tableRenames.put(table.getName(), newName);
            table.setName(newName);
        }

        ColumnRenamer visitor = new ColumnRenamer(store, tableRenames, aliasRenames);

        ExpressionList<Column> columns = insert.getColumns();
        if (columns != null) {
            for (Column column : columns) {
                column.accept(visitor);
            }
        }

        if (insert.getSelect() != null) {
            renameSelect(insert.getSelect());
        }

        Values values = insert.getValues();
        if (values != null && values.getExpressions() != null) {
            for (Expression e : values.getExpressions()) {
                e.accept(visitor);
            }
        }
    }

    private void renameUpdate(Update update) {
        Map<String, String> tableRenames = new HashMap<>();
        Map<String, String> aliasRenames = new HashMap<>();

        Table table = update.getTable();
        if (table != null) {
            String newName = store.getOrCreate(table.getName(), NameKind.TABLE);
            tableRenames.put(table.getName(), newName);
            table.setName(newName);
            renameAlias(table.getAlias(), aliasRenames);
        }
        if (update.getFromItem() != null) {
            renameFromItem(update.getFromItem(), tableRenames, aliasRenames);
        }
        if (update.getJoins() != null) {
            for (Join join : update.getJoins()) {
                renameFromItem(join.getRightItem(), tableRenames, aliasRenames);
            }
        }

        ColumnRenamer visitor = new ColumnRenamer(store, tableRenames, aliasRenames);

        for (UpdateSet updateSet : update.getUpdateSets()) {
            if (updateSet.getColumns() != null) {
                for (Column column : updateSet.getColumns()) {
                    column.accept(visitor);
                }
            }
            if (updateSet.getValues() != null) {
                for (Expression value : updateSet.getValues()) {
                    if (value != null) {
                        value.accept(visitor);
                    }
                }
            }
        }
        if (update.getWhere() != null) {
            update.getWhere().accept(visitor);
        }
        if (update.getJoins() != null) {
            for (Join join : update.getJoins()) {
                if (join.getOnExpression() != null) {
                    join.getOnExpression().accept(visitor);
                }
            }
        }
    }

    private void renameDelete(Delete delete) {
        Map<String, String> tableRenames = new HashMap<>();
        Map<String, String> aliasRenames = new HashMap<>();

        Table table = delete.getTable();
        if (table != null) {
            String newName = store.getOrCreate(table.getName(), NameKind.TABLE);
            tableRenames.put(table.getName(), newName);
            table.setName(newName);
            renameAlias(table.getAlias(), aliasRenames);
        }
        if (delete.getJoins() != null) {
            for (Join join : delete.getJoins()) {
                renameFromItem(join.getRightItem(), tableRenames, aliasRenames);
            }
        }

        ColumnRenamer visitor = new ColumnRenamer(store, tableRenames, aliasRenames);
        if (delete.getWhere() != null) {
            delete.getWhere().accept(visitor);
        }
        if (delete.getJoins() != null) {
            for (Join join : delete.getJoins()) {
                if (join.getOnExpression() != null) {
                    join.getOnExpression().accept(visitor);
                }
            }
        }
    }
}
