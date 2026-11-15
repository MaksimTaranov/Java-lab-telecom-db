package dbms.sql;

import java.util.List;

/**
 * Хранит параметры команды INSERT INTO.
 */
public class InsertCmd {
    private final String tableName;
    private final List<List<String>> rows;
    private final List<String> columnNames;

    /**
     * Создаёт описание команды вставки.
     *
     * @param tableName имя таблицы
     * @param columnNames имена явно указанных столбцов или пустой список для порядка из схемы
     * @param rows значения каждой вставляемой строки в исходном текстовом виде
     */
    public InsertCmd(String tableName, List<String> columnNames, List<List<String>> rows){
        this.tableName = tableName;
        this.columnNames = columnNames;
        this.rows = rows;
    }

    /**
     * Возвращает таблицу, в которую вставляются записи.
     *
     * @return имя таблицы
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Возвращает столбцы в порядке, указанном в запросе.
     *
     * @return список имён или пустой список для порядка из схемы
     */
    public List<String> getColumnNames() {
        return columnNames;
    }

    /**
     * Возвращает значения всех вставляемых строк.
     *
     * @return список строк, каждая из которых содержит исходные текстовые значения
     */
    public List<List<String>> getRows() {
        return rows;
    }
}
