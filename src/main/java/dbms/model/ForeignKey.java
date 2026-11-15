package dbms.model;

/**
 * Описывает связь внешнего ключа между двумя таблицами.
 */
public class ForeignKey {

    private final String tableName;
    private final String columnName;
    private final String referencedTableName;
    private final String referencedColumnName;

    /**
     * Создаёт описание внешнего ключа.
     *
     * @param tableName таблица, содержащая внешний ключ
     * @param columnName столбец внешнего ключа
     * @param referencedTableName связанная таблица
     * @param referencedColumnName столбец связанной таблицы
     */
    public ForeignKey(
            String tableName,
            String columnName,
            String referencedTableName,
            String referencedColumnName
    ) {
        this.tableName = validateName(
                tableName, "Название таблицы"
        );
        this.columnName = validateName(
                columnName, "Название столбца"
        );
        this.referencedTableName = validateName(
                referencedTableName,
                "Название связанной таблицы"
        );
        this.referencedColumnName = validateName(
                referencedColumnName,
                "Название связанного столбца"
        );
    }

    /**
     * Проверяет название и удаляет пробелы по краям.
     *
     * @param value проверяемое название
     * @param description описание поля
     * @return проверенное название
     */
    private String validateName(String value, String description) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    description + " не может быть пустым"
            );
        }

        return value.trim();
    }

    /**
     * Возвращает название таблицы с внешним ключом.
     *
     * @return название исходной таблицы
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Возвращает название столбца с внешним ключом.
     *
     * @return название исходного столбца
     */
    public String getColumnName() {
        return columnName;
    }

    /**
     * Возвращает название связанной таблицы.
     *
     * @return название целевой таблицы
     */
    public String getReferencedTableName() {
        return referencedTableName;
    }

    /**
     * Возвращает название связанного столбца.
     *
     * @return название целевого столбца
     */
    public String getReferencedColumnName() {
        return referencedColumnName;
    }
}