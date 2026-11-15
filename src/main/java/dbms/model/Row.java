package dbms.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Представляет одну запись таблицы базы данных.
 */
public class Row {

    private final TableSchema schema;
    private final Map<String, Object> values;

    /**
     * Создаёт запись и преобразует значения согласно структуре таблицы.
     *
     * @param schema структура таблицы
     * @param values значения столбцов
     */
    public Row(TableSchema schema, Map<String, Object> values) {
        this.schema = Objects.requireNonNull(
                schema, "Структура таблицы не задана"
        );

        if (values == null) {
            throw new IllegalArgumentException(
                    "Значения записи не заданы"
            );
        }

        Map<String, Object> normalizedValues = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String name = entry.getKey();

            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException(
                        "Название столбца не может быть пустым"
                );
            }

            String normalizedName = normalize(name);

            schema.getColumn(name);

            if (normalizedValues.containsKey(normalizedName)) {
                throw new IllegalArgumentException(
                        "Столбец указан несколько раз: " + name
                );
            }

            normalizedValues.put(normalizedName, entry.getValue());
        }

        this.values = new LinkedHashMap<>();

        for (Column column : schema.getColumns()) {
            Object value = normalizedValues.get(
                    normalize(column.getName())
            );

            this.values.put(
                    column.getName(),
                    column.convert(value)
            );
        }
    }

    /**
     * Возвращает структуру таблицы, которой принадлежит запись.
     *
     * @return структура таблицы
     */
    public TableSchema getSchema() {
        return schema;
    }

    /**
     * Возвращает значение столбца.
     *
     * @param columnName название столбца
     * @return значение
     */
    public Object getValue(String columnName) {
        return values.get(
                schema.getColumn(columnName).getName()
        );
    }

    /**
     * Изменяет значение столбца с проверкой его типа.
     *
     * @param columnName название столбца
     * @param value новое значение
     */
    public void setValue(String columnName, Object value) {
        Column column = schema.getColumn(columnName);

        values.put(
                column.getName(),
                column.convert(value)
        );
    }

    /**
     * Возвращает значение первичного ключа.
     *
     * @return первичный ключ записи
     */
    public Object getPrimaryKeyValue() {
        return getValue(schema.getPrimaryKey().getName());
    }

    /**
     * Возвращает значения всех столбцов.
     *
     * @return неизменяемая карта значений
     */
    public Map<String, Object> getValues() {
        return Collections.unmodifiableMap(values);
    }

    /**
     * Приводит название к единому виду для поиска.
     *
     * @param name название
     * @return нормализованное название
     */
    private String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}