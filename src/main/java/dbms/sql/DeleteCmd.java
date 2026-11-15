package dbms.sql;

/**
 * Хранит параметры команды DELETE.
 */
public class DeleteCmd {
    private final String tableName;
    private final String whereClause;

    /**
     * Создаёт описание команды удаления.
     *
     * @param tableName имя таблицы
     * @param whereClause текст условия или null, если условия нет
     */
    public DeleteCmd(String tableName, String whereClause){
        this.tableName = tableName;
        this.whereClause = whereClause;
    }

    /**
     * Возвращает условие удаления.
     *
     * @return текст условия или null, если условия нет
     */
    public String getWhereClause() {
        return whereClause;
    }

    /**
     * Возвращает таблицу, из которой удаляются записи.
     *
     * @return имя таблицы
     */
    public String getTableName() {
        return tableName;
    }
}
