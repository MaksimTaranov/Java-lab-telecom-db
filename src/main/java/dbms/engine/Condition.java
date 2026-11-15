package dbms.engine;

import dbms.model.Column;
import dbms.model.DataType;
import dbms.model.Row;

import java.util.Objects;

/**
 * Представляет условие сравнения значения столбца с заданным значением.
 */
public class Condition {

    /**
     * Поддерживаемые операции сравнения.
     */
    public enum Operator {
        EQUALS,
        NOT_EQUALS,
        GREATER,
        GREATER_OR_EQUALS,
        LESS,
        LESS_OR_EQUALS
    }

    private final String columnName;
    private final Operator operator;
    private final Object expectedValue;

    /**
     * Создаёт условие для указанного столбца.
     *
     * @param columnName название столбца
     * @param operator операция сравнения
     * @param expectedValue значение для сравнения
     */
    public Condition(
            String columnName,
            Operator operator,
            Object expectedValue
    ) {
        if (columnName == null || columnName.isBlank()) {
            throw new IllegalArgumentException(
                    "Название столбца не задано"
            );
        }

        this.columnName = columnName.trim();
        this.operator = Objects.requireNonNull(
                operator, "Операция сравнения не задана"
        );
        this.expectedValue = Objects.requireNonNull(
                expectedValue, "Значение для сравнения не задано"
        );
    }

    /**
     * Проверяет, удовлетворяет ли запись условию.
     *
     * @param row проверяемая запись
     * @return true, если запись удовлетворяет условию
     */
    public boolean matches(Row row) {
        Objects.requireNonNull(row, "Запись не может быть null");

        Column column = row.getSchema().getColumn(columnName);

        if (column.getType() == DataType.BOOLEAN
                && operator != Operator.EQUALS
                && operator != Operator.NOT_EQUALS) {
            throw new IllegalArgumentException(
                    "Для логического типа доступны только = и !="
            );
        }

        Object convertedValue = column.convert(expectedValue);
        Object actualValue = row.getValue(columnName);

        if (actualValue == null) {
            return false;
        }

        int comparison = compare(actualValue, convertedValue);

        return switch (operator) {
            case EQUALS -> comparison == 0;
            case NOT_EQUALS -> comparison != 0;
            case GREATER -> comparison > 0;
            case GREATER_OR_EQUALS -> comparison >= 0;
            case LESS -> comparison < 0;
            case LESS_OR_EQUALS -> comparison <= 0;
        };
    }

    /**
     * Сравнивает два значения.
     *
     * @param first первое значение
     * @param second второе значение
     * @return результат сравнения
     */
    private int compare(Object first, Object second) {
        if (first instanceof Number firstNumber
                && second instanceof Number secondNumber) {
            return Double.compare(
                    firstNumber.doubleValue(),
                    secondNumber.doubleValue()
            );
        }

        if (first instanceof Comparable<?> comparable) {
            @SuppressWarnings("unchecked")
            Comparable<Object> value =
                    (Comparable<Object>) comparable;

            return value.compareTo(second);
        }

        throw new IllegalArgumentException(
                "Значения этих типов нельзя сравнить"
        );
    }

    /**
     * Возвращает название столбца.
     *
     * @return название столбца
     */
    public String getColumnName() {
        return columnName;
    }

    /**
     * Возвращает операцию сравнения.
     *
     * @return операция сравнения
     */
    public Operator getOperator() {
        return operator;
    }

    /**
     * Возвращает значение для сравнения.
     *
     * @return ожидаемое значение
     */
    public Object getExpectedValue() {
        return expectedValue;
    }
}