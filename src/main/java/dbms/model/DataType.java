package dbms.model;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Типы данных, поддерживаемые базой данных.
 */
public enum DataType {

    INTEGER(Integer.class),
    DOUBLE(Double.class),
    STRING(String.class),
    DATE(LocalDate.class),
    BOOLEAN(Boolean.class);

    private final Class<?> javaClass;

    DataType(Class<?> javaClass) {
        this.javaClass = javaClass;
    }

    /**
     * Возвращает соответствующий класс Java.
     *
     * @return класс Java
     */
    public Class<?> getJavaClass() {
        return javaClass;
    }

    /**
     * Преобразует значение к текущему типу данных.
     *
     * @param value исходное значение
     * @return преобразованное значение
     */
    public Object convert(Object value) {
        if (value == null) {
            return null;
        }

        if (javaClass.isInstance(value)) {
            return value;
        }

        if (this == STRING) {
            return value.toString();
        }

        String text = value.toString().trim();

        try {
            return switch (this) {
                case INTEGER -> Integer.parseInt(text);
                case DOUBLE -> Double.parseDouble(text);
                case STRING -> value.toString();
                case DATE -> LocalDate.parse(text);
                case BOOLEAN -> {
                    if (!text.equalsIgnoreCase("true")
                            && !text.equalsIgnoreCase("false")) {
                        throw new IllegalArgumentException(
                                "Недопустимое логическое значение: " + text
                        );
                    }

                    yield Boolean.parseBoolean(text);
                }
            };
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Не удалось преобразовать значение '" + text
                            + "' к типу " + this,
                    e
            );
        }
    }
}