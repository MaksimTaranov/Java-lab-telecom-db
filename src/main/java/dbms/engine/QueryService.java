package dbms.engine;

import dbms.index.IndexManager;
import dbms.model.Column;
import dbms.model.ForeignKey;
import dbms.model.Row;
import dbms.model.Table;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Выполняет операции выборки, изменения и удаления записей базы данных.
 */
public class QueryService {

    private final Database database;

    /**
     * Создаёт сервис для работы с запросами.
     *
     * @param database база данных
     */
    public QueryService(Database database) {
        this.database = Objects.requireNonNull(
                database, "База данных не задана"
        );
    }

    /**
     * Выбирает записи из таблицы, удовлетворяющие условию.
     *
     * Для точного сравнения используется индекс, если он создан
     * для указанного столбца. В остальных случаях выполняется перебор.
     *
     * @param tableName название таблицы
     * @param condition условие отбора; null означает выбор всех записей
     * @return список найденных записей
     */
    public List<Row> selectRows(String tableName, Condition condition) {
        Table table = getTable(tableName);

        if (condition == null) {
            return List.copyOf(table.getRows());
        }

        Column column = table.getSchema().getColumn(
                condition.getColumnName()
        );

        if (condition.getOperator() == Condition.Operator.EQUALS) {
            IndexManager indexManager = database.getIndexManager();

            if (indexManager.hasIndex(
                    tableName,
                    column.getName()
            )) {
                return selectUsingIndex(
                        table,
                        condition,
                        column,
                        indexManager
                );
            }
        }

        return selectByScanning(table, condition);
    }

    /**
     * Выполняет выборку с использованием индекса.
     *
     * @param table таблица
     * @param condition условие отбора
     * @param column столбец с индексом
     * @param indexManager менеджер индексов
     * @return найденные записи
     */
    private List<Row> selectUsingIndex(
            Table table,
            Condition condition,
            Column column,
            IndexManager indexManager
    ) {
        Object value = column.convert(condition.getExpectedValue());

        Set<Object> primaryKeys = indexManager.findEqual(
                table.getName(),
                column.getName(),
                value
        );

        List<Row> result = new ArrayList<>();

        for (Object primaryKey : primaryKeys) {
            Row row = table.getRow(primaryKey);

            if (row != null && condition.matches(row)) {
                result.add(row);
            }
        }

        return List.copyOf(result);
    }

    /**
     * Выполняет выборку последовательным перебором записей.
     *
     * @param table таблица
     * @param condition условие отбора
     * @return найденные записи
     */
    private List<Row> selectByScanning(
            Table table,
            Condition condition
    ) {
        List<Row> result = new ArrayList<>();

        for (Row row : table.getRows()) {
            if (condition.matches(row)) {
                result.add(row);
            }
        }

        return List.copyOf(result);
    }

    /**
     * Изменяет значения указанных столбцов во всех подходящих записях.
     *
     * @param tableName название таблицы
     * @param condition условие отбора; null означает все записи
     * @param newValues новые значения столбцов
     * @return количество изменённых записей
     */
    public int updateRows(
            String tableName,
            Condition condition,
            Map<String, Object> newValues
    ) {
        Table table = getTable(tableName);

        if (newValues == null || newValues.isEmpty()) {
            throw new IllegalArgumentException(
                    "Не указаны новые значения"
            );
        }

        List<Row> rows = selectRows(tableName, condition);

        if (rows.isEmpty()) {
            return 0;
        }

        Map<String, Object> convertedValues = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : newValues.entrySet()) {
            Column column = table.getSchema().getColumn(entry.getKey());

            if (column.isPrimaryKey()) {
                throw new IllegalArgumentException(
                        "Изменение первичного ключа пока не поддерживается"
                );
            }

            Object convertedValue = column.convert(entry.getValue());

            convertedValues.put(column.getName(), convertedValue);
        }

        validateForeignKeyUpdates(tableName, convertedValues);

        IndexManager indexManager = database.getIndexManager();

        for (Row row : rows) {
            for (Map.Entry<String, Object> entry
                    : convertedValues.entrySet()) {

                String columnName = entry.getKey();
                Object newValue = entry.getValue();
                Object oldValue = row.getValue(columnName);

                row.setValue(columnName, newValue);

                indexManager.onUpdate(
                        table,
                        row,
                        columnName,
                        oldValue,
                        newValue
                );
            }
        }

        return rows.size();
    }

    /**
     * Проверяет новые значения столбцов, являющихся внешними ключами.
     *
     * @param tableName название изменяемой таблицы
     * @param newValues новые значения столбцов
     */
    private void validateForeignKeyUpdates(
            String tableName,
            Map<String, Object> newValues
    ) {
        for (ForeignKey foreignKey : database.getForeignKeys()) {
            if (!foreignKey.getTableName().equalsIgnoreCase(tableName)) {
                continue;
            }

            String columnName = foreignKey.getColumnName();

            if (!newValues.containsKey(columnName)) {
                continue;
            }

            Object referenceValue = newValues.get(columnName);

            if (referenceValue == null) {
                continue;
            }

            Table referencedTable = getTable(
                    foreignKey.getReferencedTableName()
            );

            if (referencedTable.getRow(referenceValue) == null) {
                throw new IllegalArgumentException(
                        "Связанная запись не найдена: "
                                + foreignKey.getReferencedTableName()
                                + ", ключ: " + referenceValue
                );
            }
        }
    }

    /**
     * Удаляет записи, удовлетворяющие условию.
     *
     * @param tableName название таблицы
     * @param condition условие отбора; null означает все записи
     * @param cascade разрешено ли каскадное удаление связанных записей
     * @return количество удалённых записей
     */
    public int deleteRows(
            String tableName,
            Condition condition,
            boolean cascade
    ) {
        getTable(tableName);

        List<Row> rows = selectRows(tableName, condition);

        if (rows.isEmpty()) {
            return 0;
        }

        // Сначала проверяем все записи, чтобы не удалить только часть.
        if (!cascade) {
            for (Row row : rows) {
                if (hasReferences(tableName, row)) {
                    throw new IllegalStateException(
                            "Нельзя удалить запись: на неё ссылаются другие таблицы"
                    );
                }
            }
        }

        for (Row row : rows) {
            database.deleteRow(
                    tableName,
                    row.getPrimaryKeyValue(),
                    cascade
            );
        }

        return rows.size();
    }

    private boolean hasReferences(String tableName, Row targetRow) {
        for (ForeignKey foreignKey : database.getForeignKeys()) {
            if (!foreignKey.getReferencedTableName()
                    .equalsIgnoreCase(tableName)) {
                continue;
            }

            Table sourceTable = getTable(foreignKey.getTableName());

            Object targetValue = targetRow.getValue(
                    foreignKey.getReferencedColumnName()
            );

            for (Row sourceRow : sourceTable.getRows()) {
                Object sourceValue = sourceRow.getValue(
                        foreignKey.getColumnName()
                );

                if (Objects.equals(targetValue, sourceValue)) {
                    return true;
                }
            }
        }

        return false;
    }

    private Table getTable(String tableName) {
        return database.getTable(tableName);
    }
}