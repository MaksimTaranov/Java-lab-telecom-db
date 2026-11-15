package dbms.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Описывает столбец таблицы базы данных.
 */
public class Column {

    private final String name;
    private final DataType type;
    private final boolean primaryKey;
    private final boolean nullable;

    /**
     * Создаёт описание столбца.
     *
     * @param name название столбца
     * @param type тип данных
     * @param primaryKey является ли столбец первичным ключом
     * @param nullable допускаются ли пустые значения
     */
    public Column(
            String name,
            DataType type,
            boolean primaryKey,
            boolean nullable
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Название столбца не может быть пустым"
            );
        }

        if (primaryKey && nullable) {
            throw new IllegalArgumentException(
                    "Первичный ключ не может быть пустым"
            );
        }

        this.name = name.trim();
        this.type = Objects.requireNonNull(
                type, "Тип данных не задан"
        );
        this.primaryKey = primaryKey;
        this.nullable = nullable;
    }

    /**
     * Возвращает название столбца.
     *
     * @return название столбца
     */
    public String getName() {
        return name;
    }

    /**
     * Возвращает тип данных столбца.
     *
     * @return тип данных
     */
    public DataType getType() {
        return type;
    }

    /**
     * Проверяет, является ли столбец первичным ключом.
     *
     * @return true, если столбец является первичным ключом
     */
    public boolean isPrimaryKey() {
        return primaryKey;
    }

    /**
     * Проверяет, допускает ли столбец пустые значения.
     *
     * @return true, если пустые значения разрешены
     */
    public boolean isNullable() {
        return nullable;
    }

    /**
     * Преобразует и проверяет значение столбца.
     *
     * @param value исходное значение
     * @return преобразованное значение
     */
    public Object convert(Object value) {
        if (value == null) {
            if (!nullable) {
                throw new IllegalArgumentException(
                        "Столбец " + name
                                + " не может содержать пустое значение"
                );
            }

            return null;
        }

        return type.convert(value);
    }

    /**
     * Сравнивает столбцы по названию без учёта регистра.
     *
     * @param obj объект для сравнения
     * @return true, если названия совпадают
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof Column column)) {
            return false;
        }

        return name.equalsIgnoreCase(column.name);
    }

    /**
     * Возвращает хеш-код названия столбца.
     *
     * @return хеш-код
     */
    @Override
    public int hashCode() {
        return name.toLowerCase(Locale.ROOT).hashCode();
    }
}