package dbms.sql;

import java.util.Map;

/**
 * Хранит параметры команды UPDATE
 */
public class UpdateCmd {
    private final String tableName;
    private final Map<String, Object> newValues;
    private final String whereClause;

    /**
     * Создаёт описание команды обновления.
     *
     * @param tableName имя таблицы
     * @param newValues новые значения по именам столбцов
     * @param whereClause текст условия или null, если условия нет
     */
    public UpdateCmd(String tableName, Map<String, Object> newValues, String whereClause){
        this.tableName = tableName;
        this.newValues = newValues;
        this.whereClause = whereClause;
    }

    /**
     * Возвращает таблицу, в которой обновляются записи.
     *
     * @return имя таблицы
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Возвращает условие обновления.
     *
     * @return текст условия или null, если условия нет
     */
    public String getWhereClause() {
        return whereClause;
    }

    /**
     * Возвращает новые значения для присваиваний после SET
     *
     * @return соответствие между именами столбцов и новыми значениями
     */
    public Map<String, Object> getNewValues() {
        return newValues;
    }
}
