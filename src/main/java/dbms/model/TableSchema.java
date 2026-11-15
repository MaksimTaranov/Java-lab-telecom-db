package dbms.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Хранит структуру таблицы: название, столбцы и первичный ключ.
 */
public class TableSchema {

    private final String tableName;
    private final List<Column> columns;
    private final Column primaryKey;

    /**
     * Создаёт структуру таблицы.
     *
     * @param tableName название таблицы
     * @param columns список столбцов
     */
    public TableSchema(String tableName, List<Column> columns) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException(
                    "Название таблицы не может быть пустым"
            );
        }

        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException(
                    "Таблица должна содержать столбцы"
            );
        }

        this.tableName = tableName.trim();
        this.columns = new ArrayList<>();

        Column foundPrimaryKey = null;

        for (Column column : columns) {
            Objects.requireNonNull(
                    column, "Столбец не может быть null"
            );

            if (this.columns.contains(column)) {
                throw new IllegalArgumentException(
                        "Повторяющийся столбец: " + column.getName()
                );
            }

            this.columns.add(column);

            if (column.isPrimaryKey()) {
                if (foundPrimaryKey != null) {
                    throw new IllegalArgumentException(
                            "Таблица может иметь только один первичный ключ"
                    );
                }

                foundPrimaryKey = column;
            }
        }

        if (foundPrimaryKey == null) {
            throw new IllegalArgumentException(
                    "В таблице должен быть первичный ключ"
            );
        }

        this.primaryKey = foundPrimaryKey;
    }

    /**
     * Возвращает название таблицы.
     *
     * @return название таблицы
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Возвращает список столбцов.
     *
     * @return неизменяемый список столбцов
     */
    public List<Column> getColumns() {
        return Collections.unmodifiableList(columns);
    }

    /**
     * Возвращает первичный ключ таблицы.
     *
     * @return столбец первичного ключа
     */
    public Column getPrimaryKey() {
        return primaryKey;
    }

    /**
     * Находит столбец по названию без учёта регистра.
     *
     * @param name название столбца
     * @return найденный столбец
     */
    public Column getColumn(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Название столбца не задано"
            );
        }

        String normalizedName = name.trim();

        for (Column column : columns) {
            if (column.getName().equalsIgnoreCase(normalizedName)) {
                return column;
            }
        }

        throw new IllegalArgumentException(
                "Столбец " + name
                        + " не найден в таблице " + tableName
        );
    }
}