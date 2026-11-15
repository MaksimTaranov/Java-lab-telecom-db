package dbms.engine;

import dbms.index.IndexManager;
import dbms.model.Column;
import dbms.model.ForeignKey;
import dbms.model.Row;
import dbms.model.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Представляет базу данных и управляет её таблицами,
 * связями и индексами.
 */
public class Database {

    private final String name;
    private final Map<String, Table> tables;
    private final List<ForeignKey> foreignKeys;
    private final IndexManager indexManager;

    /**
     * Создаёт базу данных.
     *
     * @param name название базы данных
     */
    public Database(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Название базы данных не может быть пустым"
            );
        }

        this.name = name;
        this.tables = new LinkedHashMap<>();
        this.foreignKeys = new ArrayList<>();
        this.indexManager = new IndexManager();
    }

    /**
     * Возвращает название базы данных.
     *
     * @return название базы данных
     */
    public String getName() {
        return name;
    }

    /**
     * Возвращает менеджер индексов базы данных.
     *
     * @return менеджер индексов
     */
    public IndexManager getIndexManager() {
        return indexManager;
    }

    /**
     * Создаёт индекс для указанного столбца.
     *
     * @param tableName название таблицы
     * @param columnName название столбца
     */
    public void createIndex(String tableName, String columnName) {
        indexManager.createIndex(this, tableName, columnName);
    }

    /**
     * Добавляет таблицу в базу данных.
     *
     * @param table добавляемая таблица
     */
    public void addTable(Table table) {
        Objects.requireNonNull(table, "Таблица не может быть null");

        String key = normalize(table.getName());

        if (tables.containsKey(key)) {
            throw new IllegalArgumentException(
                    "Таблица " + table.getName() + " уже существует"
            );
        }

        tables.put(key, table);
    }

    /**
     * Возвращает таблицу по названию.
     *
     * @param tableName название таблицы
     * @return найденная таблица
     */
    public Table getTable(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException(
                    "Название таблицы не задано"
            );
        }

        Table table = tables.get(normalize(tableName));

        if (table == null) {
            throw new IllegalArgumentException(
                    "Таблица " + tableName + " не найдена"
            );
        }

        return table;
    }

    /**
     * Удаляет таблицу вместе с её записями, индексами и объявленными в ней внешними ключами.
     *
     * @param tableName имя удаляемой таблицы
     * @throws IllegalArgumentException если имя не задано или таблица не найдена
     * @throws IllegalStateException если на таблицу ссылается другая таблица
     */
    public void dropTable(String tableName) {
        Table table = getTable(tableName);
        String name = table.getName();

        for (ForeignKey key : foreignKeys) {
            if (key.getReferencedTableName().equalsIgnoreCase(name)
                    && !key.getTableName().equalsIgnoreCase(name)) {
                throw new IllegalStateException(
                        "Нельзя удалить таблицу: на неё ссылается " + key.getTableName()
                );
            }
        }

        for (int i = foreignKeys.size() - 1; i >= 0; i--) {
            if (foreignKeys.get(i).getTableName().equalsIgnoreCase(name)) {
                foreignKeys.remove(i);
            }
        }

        indexManager.removeTableIndexes(name);
        tables.remove(normalize(name));
    }

    /**
     * Возвращает все зарегистрированные таблицы.
     *
     * @return неизменяемая карта таблиц
     */
    public Map<String, Table> getTables() {
        return Collections.unmodifiableMap(tables);
    }

    /**
     * Добавляет внешний ключ после проверки связи между таблицами.
     *
     * @param foreignKey описание внешнего ключа
     */
    public void addForeignKey(ForeignKey foreignKey) {
        Objects.requireNonNull(
                foreignKey, "Внешний ключ не может быть null"
        );

        Table sourceTable = getTable(foreignKey.getTableName());
        Table targetTable = getTable(
                foreignKey.getReferencedTableName()
        );

        Column sourceColumn = sourceTable.getSchema()
                .getColumn(foreignKey.getColumnName());

        Column targetColumn = targetTable.getSchema()
                .getColumn(foreignKey.getReferencedColumnName());

        if (!targetColumn.isPrimaryKey()) {
            throw new IllegalArgumentException(
                    "Внешний ключ должен ссылаться на первичный ключ"
            );
        }

        if (sourceColumn.getType() != targetColumn.getType()) {
            throw new IllegalArgumentException(
                    "Типы связанных столбцов должны совпадать"
            );
        }

        for (ForeignKey existing : foreignKeys) {
            if (existing.getTableName().equalsIgnoreCase(
                    foreignKey.getTableName()
            ) && existing.getColumnName().equalsIgnoreCase(
                    foreignKey.getColumnName()
            )) {
                throw new IllegalArgumentException(
                        "Для этого столбца внешний ключ уже существует"
                );
            }
        }

        for (Row row : sourceTable.getRows()) {
            Object value = row.getValue(sourceColumn.getName());

            if (value != null && targetTable.getRow(value) == null) {
                throw new IllegalArgumentException(
                        "Существующая запись содержит неверный внешний ключ: "
                                + value
                );
            }
        }

        foreignKeys.add(foreignKey);
    }

    /**
     * Возвращает все внешние ключи базы данных.
     *
     * @return неизменяемый список внешних ключей
     */
    public List<ForeignKey> getForeignKeys() {
        return Collections.unmodifiableList(foreignKeys);
    }

    /**
     * Добавляет одну запись с проверкой внешних ключей.
     *
     * @param tableName название таблицы
     * @param row добавляемая запись
     */
    public void insertRow(String tableName, Row row) {
        Objects.requireNonNull(row, "Запись не может быть null");

        insertRows(tableName, List.of(row));
    }

    /**
     * Добавляет несколько записей после проверки внешних ключей.
     *
     * @param tableName название таблицы
     * @param newRows список добавляемых записей
     */
    public void insertRows(String tableName, List<Row> newRows) {
        Objects.requireNonNull(newRows, "Список записей не задан");

        Table table = getTable(tableName);

        for (Row row : newRows) {
            Objects.requireNonNull(row, "Запись не может быть null");

            if (row.getSchema() != table.getSchema()) {
                throw new IllegalArgumentException(
                        "Запись создана для другой структуры таблицы"
                );
            }

            validateForeignKeys(table, row);
        }

        table.addRows(newRows);

        for (Row row : newRows) {
            indexManager.onInsert(table, row);
        }
    }

    private void validateForeignKeys(Table table, Row row) {
        for (ForeignKey foreignKey : foreignKeys) {
            if (!foreignKey.getTableName().equalsIgnoreCase(
                    table.getName()
            )) {
                continue;
            }

            Object referenceValue = row.getValue(
                    foreignKey.getColumnName()
            );

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
     * Удаляет запись и при необходимости связанные с ней записи.
     *
     * @param tableName название таблицы
     * @param primaryKey значение первичного ключа
     * @param cascade разрешено ли каскадное удаление
     * @return true, если запись была удалена
     */
    public boolean deleteRow(
            String tableName,
            Object primaryKey,
            boolean cascade
    ) {
        Table table = getTable(tableName);

        Row targetRow = table.getRow(primaryKey);

        if (targetRow == null) {
            return false;
        }

        Map<Table, Set<Object>> dependentRows = new LinkedHashMap<>();

        for (ForeignKey foreignKey : foreignKeys) {
            if (!foreignKey.getReferencedTableName()
                    .equalsIgnoreCase(tableName)) {
                continue;
            }

            Table dependentTable = getTable(
                    foreignKey.getTableName()
            );

            for (Row row : dependentTable.getRows()) {
                Object referenceValue = row.getValue(
                        foreignKey.getColumnName()
                );

                if (Objects.equals(referenceValue, primaryKey)) {
                    dependentRows.computeIfAbsent(
                            dependentTable,
                            key -> new LinkedHashSet<>()
                    ).add(row.getPrimaryKeyValue());
                }
            }
        }

        boolean hasDependencies = dependentRows.values()
                .stream()
                .anyMatch(keys -> !keys.isEmpty());

        if (hasDependencies && !cascade) {
            throw new IllegalStateException(
                    "У записи есть связанные данные. "
                            + "Требуется подтверждение каскадного удаления"
            );
        }

        for (Map.Entry<Table, Set<Object>> entry
                : dependentRows.entrySet()) {
            Table dependentTable = entry.getKey();

            for (Object key : entry.getValue()) {
                Row dependentRow = dependentTable.getRow(key);

                if (dependentRow != null) {
                    indexManager.onDelete(dependentTable, dependentRow);
                    dependentTable.removeRow(key);
                }
            }
        }

        indexManager.onDelete(table, targetRow);

        return table.removeRow(primaryKey);
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}