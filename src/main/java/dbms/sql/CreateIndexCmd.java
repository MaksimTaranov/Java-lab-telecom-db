package dbms.sql;

/**
 * Хранит параметры команды CREATE INDEX.
 */
public class CreateIndexCmd {
    private final String tableName;
    private final String columnName;

    /**
     * Создаёт описание команды создания индекса.
     *
     * @param tableName имя таблицы
     * @param columnName имя индексируемого столбца
     */
    public CreateIndexCmd(String tableName, String columnName){
        this.tableName = tableName;
        this.columnName = columnName;
    }

    /**
     * Возвращает таблицу, для которой создаётся индекс.
     *
     * @return имя таблицы
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Возвращает столбец, по которому создаётся индекс.
     *
     * @return имя индексируемого столбца
     */
    public String getColumnName() {
        return columnName;
    }
}
