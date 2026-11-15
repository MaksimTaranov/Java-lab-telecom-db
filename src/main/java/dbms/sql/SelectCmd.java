package dbms.sql;

import java.util.List;

/**
 * Хранит параметры команды SELECT.
 */
public class SelectCmd {
    private final String tableName;
    private final List<String> columnNames;
    private final String whereClause;

    /**
     * Создаёт описание команды выборки.
     *
     * @param tableName имя таблицы
     * @param columnNames имена выбранных столбцов или пустой список для *
     * @param whereClause текст условия или null, если условия нет
     */
    public SelectCmd(String tableName, List<String> columnNames, String whereClause){
        this.tableName = tableName;
        this.columnNames = columnNames;
        this.whereClause = whereClause;
    }

    /**
     * Возвращает условие выборки.
     *
     * @return текст условия или null, если условия нет
     */
    public String getWhereClause() {
        return whereClause;
    }

    /**
     * Возвращает таблицу, из которой выбираются записи.
     *
     * @return имя таблицы
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Возвращает столбцы в порядке, указанном в запросе.
     *
     * @return список имён или пустой список при выборке всех столбцов
     */
    public List<String> getColumnNames() {
        return columnNames;
    }
}
