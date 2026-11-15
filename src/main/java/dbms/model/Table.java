package dbms.model;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Представляет таблицу и хранит её записи.
 */
public class Table {
    private final TableSchema schema;
    private final Map<Object, Row> rows;

    /**
     * Создаёт таблицу с заданной структурой.
     *
     * @param schema структура таблицы
     */
    public Table(TableSchema schema) {
        this.schema = Objects.requireNonNull(
                schema, "Структура таблицы не задана"
        );
        this.rows = new LinkedHashMap<>();
    }

    /**
     * Возвращает структуру таблицы.
     *
     * @return структура таблицы
     */
    public TableSchema getSchema() {
        return schema;
    }

    /**
     * Возвращает название таблицы.
     *
     * @return название таблицы
     */
    public String getName() {
        return schema.getTableName();
    }

    /**
     * Добавляет одну запись с проверкой уникальности ключа.
     *
     * @param row добавляемая запись
     */
    public void addRow(Row row) {
        Objects.requireNonNull(row, "Запись не может быть null");

        if (row.getSchema() != schema) {
            throw new IllegalArgumentException(
                    "Запись создана для другой структуры таблицы"
            );
        }

        Object key = row.getPrimaryKeyValue();

        if (rows.containsKey(key)) {
            throw new IllegalArgumentException(
                    "Запись с первичным ключом " + key + " уже существует"
            );
        }

        rows.put(key, row);
    }

    /**
     * Добавляет несколько записей после проверки всех ключей.
     *
     * @param newRows список добавляемых записей
     */
    public void addRows(List<Row> newRows) {
        Objects.requireNonNull(newRows, "Список записей не задан");

        Set<Object> keys = new HashSet<>();

        for (Row row : newRows) {
            Objects.requireNonNull(row, "Запись не может быть null");

            if (row.getSchema() != schema) {
                throw new IllegalArgumentException(
                        "Запись создана для другой структуры таблицы"
                );
            }

            Object key = row.getPrimaryKeyValue();

            if (rows.containsKey(key) || !keys.add(key)) {
                throw new IllegalArgumentException(
                        "Повторяющийся первичный ключ: " + key
                );
            }
        }

        for (Row row : newRows) {
            rows.put(row.getPrimaryKeyValue(), row);
        }
    }

    /**
     * Возвращает запись по первичному ключу.
     *
     * @param primaryKey значение первичного ключа
     * @return найденная запись или null
     */
    public Row getRow(Object primaryKey) {
        return rows.get(primaryKey);
    }

    /**
     * Удаляет запись по первичному ключу.
     *
     * @param primaryKey значение первичного ключа
     * @return true, если запись была удалена
     */
    public boolean removeRow(Object primaryKey) {
        return rows.remove(primaryKey) != null;
    }

    /**
     * Возвращает все записи таблицы.
     *
     * @return неизменяемый список записей
     */
    public List<Row> getRows() {
        return List.copyOf(rows.values());
    }

    /**
     * Возвращает количество записей.
     *
     * @return количество записей
     */
    public int size() {
        return rows.size();
    }

    /**
     * Проверяет, пуста ли таблица.
     *
     * @return true, если таблица не содержит записей
     */
    public boolean isEmpty() {
        return rows.isEmpty();
    }
}
