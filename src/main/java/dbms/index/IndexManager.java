package dbms.index;

import dbms.engine.Database;
import dbms.model.Column;
import dbms.model.Row;
import dbms.model.Table;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Управляет индексами таблиц базы данных.
 *
 * Индекс хранит соответствие между значением столбца
 * и первичными ключами записей с этим значением.
 */
public class IndexManager {

    private static final int MAGIC = 0x494E4458;
    private static final int VERSION = 1;
    private static final int MAX_INDEXES = 10_000;

    private final Map<IndexKey, Map<Object, Set<Object>>> indexes =
            new LinkedHashMap<>();

    /**
     * Создаёт индекс для указанного столбца.
     *
     * @param database база данных
     * @param tableName название таблицы
     * @param columnName название столбца
     */
    public void createIndex(
            Database database,
            String tableName,
            String columnName
    ) {
        Objects.requireNonNull(database, "База данных не задана");

        Table table = database.getTable(tableName);
        Column column = table.getSchema().getColumn(columnName);

        IndexKey key = new IndexKey(
                normalize(tableName),
                normalize(columnName)
        );

        if (indexes.containsKey(key)) {
            throw new IllegalArgumentException(
                    "Индекс для этого столбца уже существует"
            );
        }

        Map<Object, Set<Object>> values = new HashMap<>();

        for (Row row : table.getRows()) {
            addValue(
                    values,
                    row.getValue(column.getName()),
                    row.getPrimaryKeyValue()
            );
        }

        indexes.put(key, values);
    }

    /**
     * Проверяет наличие индекса для столбца.
     *
     * @param tableName название таблицы
     * @param columnName название столбца
     * @return true, если индекс существует
     */
    public boolean hasIndex(String tableName, String columnName) {
        return indexes.containsKey(
                new IndexKey(
                        normalize(tableName),
                        normalize(columnName)
                )
        );
    }

    /**
     * Возвращает первичные ключи записей с указанным значением.
     *
     * @param tableName название таблицы
     * @param columnName название столбца
     * @param value искомое значение
     * @return найденные первичные ключи
     */
    public Set<Object> findEqual(
            String tableName,
            String columnName,
            Object value
    ) {
        IndexKey key = new IndexKey(
                normalize(tableName),
                normalize(columnName)
        );

        Map<Object, Set<Object>> index = indexes.get(key);

        if (index == null) {
            throw new IllegalArgumentException(
                    "Индекс для указанного столбца не существует"
            );
        }

        Set<Object> result = index.get(value);

        if (result == null) {
            return Set.of();
        }

        return new LinkedHashSet<>(result);
    }

    /**
     * Добавляет запись во все индексы её таблицы.
     *
     * Вызывается после успешной вставки записи.
     *
     * @param table таблица
     * @param row добавленная запись
     */
    public void onInsert(Table table, Row row) {
        String tableName = normalize(table.getName());

        for (Map.Entry<IndexKey, Map<Object, Set<Object>>> entry
                : indexes.entrySet()) {

            if (entry.getKey().tableName().equals(tableName)) {
                String columnName = entry.getKey().columnName();

                Object value = row.getValue(
                        table.getSchema().getColumn(columnName).getName()
                );

                addValue(
                        entry.getValue(),
                        value,
                        row.getPrimaryKeyValue()
                );
            }
        }
    }

    /**
     * Удаляет запись из всех индексов её таблицы.
     *
     * Вызывается до удаления записи.
     *
     * @param table таблица
     * @param row удаляемая запись
     */
    public void onDelete(Table table, Row row) {
        String tableName = normalize(table.getName());

        for (Map.Entry<IndexKey, Map<Object, Set<Object>>> entry
                : indexes.entrySet()) {

            if (entry.getKey().tableName().equals(tableName)) {
                String columnName = entry.getKey().columnName();

                Object value = row.getValue(
                        table.getSchema().getColumn(columnName).getName()
                );

                removeValue(
                        entry.getValue(),
                        value,
                        row.getPrimaryKeyValue()
                );
            }
        }
    }

    /**
     * Обновляет индекс после изменения значения столбца.
     *
     * @param table таблица
     * @param row изменённая запись
     * @param columnName название изменённого столбца
     * @param oldValue прежнее значение
     * @param newValue новое значение
     */
    public void onUpdate(
            Table table,
            Row row,
            String columnName,
            Object oldValue,
            Object newValue
    ) {
        IndexKey key = new IndexKey(
                normalize(table.getName()),
                normalize(columnName)
        );

        Map<Object, Set<Object>> index = indexes.get(key);

        if (index == null) {
            return;
        }

        Object primaryKey = row.getPrimaryKeyValue();

        removeValue(index, oldValue, primaryKey);
        addValue(index, newValue, primaryKey);
    }

    /**
     * Перестраивает все индексы по текущему содержимому базы данных.
     *
     * @param database база данных
     */
    public void rebuild(Database database) {
        Objects.requireNonNull(database, "База данных не задана");

        Map<IndexKey, Map<Object, Set<Object>>> rebuilt =
                new LinkedHashMap<>();

        for (IndexKey key : indexes.keySet()) {
            Table table = database.getTable(key.tableName());

            Column column = table.getSchema().getColumn(
                    key.columnName()
            );

            Map<Object, Set<Object>> values = new HashMap<>();

            for (Row row : table.getRows()) {
                addValue(
                        values,
                        row.getValue(column.getName()),
                        row.getPrimaryKeyValue()
                );
            }

            rebuilt.put(key, values);
        }

        indexes.clear();
        indexes.putAll(rebuilt);
    }

    /**
     * Сохраняет описания индексов во временный файл,
     * после чего заменяет основной файл.
     *
     * @param file путь к файлу
     * @throws IOException если произошла ошибка записи
     */
    public void save(Path file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException(
                    "Путь к файлу не задан"
            );
        }

        if (indexes.size() > MAX_INDEXES) {
            throw new IOException(
                    "Превышено допустимое количество индексов"
            );
        }

        Path absolutePath = file.toAbsolutePath();
        Path parent = absolutePath.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temporaryFile = absolutePath.resolveSibling(
                absolutePath.getFileName() + ".tmp"
        );

        boolean saved = false;

        try {
            try (DataOutputStream output = new DataOutputStream(
                    Files.newOutputStream(temporaryFile)
            )) {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                output.writeInt(indexes.size());

                for (IndexKey key : indexes.keySet()) {
                    output.writeUTF(key.tableName());
                    output.writeUTF(key.columnName());
                }
            }

            try {
                Files.move(
                        temporaryFile,
                        absolutePath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(
                        temporaryFile,
                        absolutePath,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            saved = true;
        } finally {
            if (!saved) {
                Files.deleteIfExists(temporaryFile);
            }
        }
    }

    /**
     * Загружает описания индексов и перестраивает их.
     *
     * @param file путь к файлу
     * @param database база данных, по которой строятся индексы
     * @throws IOException если файл повреждён или не может быть прочитан
     */
    public void load(Path file, Database database) throws IOException {
        if (file == null || database == null) {
            throw new IllegalArgumentException(
                    "Файл и база данных должны быть заданы"
            );
        }

        Map<IndexKey, Map<Object, Set<Object>>> loaded =
                new LinkedHashMap<>();

        try (DataInputStream input = new DataInputStream(
                Files.newInputStream(file)
        )) {
            if (input.readInt() != MAGIC) {
                throw new IOException(
                        "Файл индексов имеет неверный формат"
                );
            }

            int version = input.readInt();

            if (version != VERSION) {
                throw new IOException(
                        "Неподдерживаемая версия файла индексов: " + version
                );
            }

            int count = input.readInt();

            if (count < 0 || count > MAX_INDEXES) {
                throw new IOException(
                        "Некорректное количество индексов: " + count
                );
            }

            for (int i = 0; i < count; i++) {
                IndexKey key = new IndexKey(
                        normalize(input.readUTF()),
                        normalize(input.readUTF())
                );

                if (loaded.containsKey(key)) {
                    throw new IOException(
                            "В файле обнаружен повторный индекс"
                    );
                }

                try {
                    Table table = database.getTable(key.tableName());

                    table.getSchema().getColumn(key.columnName());
                } catch (IllegalArgumentException e) {
                    throw new IOException(
                            "Индекс ссылается на неизвестную таблицу или столбец",
                            e
                    );
                }

                loaded.put(key, new HashMap<>());
            }

            if (input.read() != -1) {
                throw new IOException(
                        "В файле индексов обнаружены лишние данные"
                );
            }

        } catch (IllegalArgumentException e) {
            throw new IOException(
                    "Файл индексов повреждён",
                    e
            );
        }

        indexes.clear();
        indexes.putAll(loaded);

        rebuild(database);
    }

    /**
     * Удаляет все индексы указанной таблицы.
     *
     * @param tableName имя таблицы
     * @throws IllegalArgumentException если имя таблицы не задано
     */
    public void removeTableIndexes(String tableName) {
        String name = normalize(tableName);
        List<IndexKey> keysToRemove = new ArrayList<>();

        for (IndexKey key : indexes.keySet()) {
            if (key.tableName().equals(name)) {
                keysToRemove.add(key);
            }
        }

        for (IndexKey key : keysToRemove) {
            indexes.remove(key);
        }
    }

    /**
     * Добавляет первичный ключ в индекс по значению столбца.
     *
     * @param index индекс
     * @param value значение столбца
     * @param primaryKey первичный ключ записи
     */
    private void addValue(
            Map<Object, Set<Object>> index,
            Object value,
            Object primaryKey
    ) {
        index.computeIfAbsent(
                value,
                ignored -> new LinkedHashSet<>()
        ).add(primaryKey);
    }

    /**
     * Удаляет первичный ключ из индекса.
     *
     * @param index индекс
     * @param value значение столбца
     * @param primaryKey первичный ключ записи
     */
    private void removeValue(
            Map<Object, Set<Object>> index,
            Object value,
            Object primaryKey
    ) {
        Set<Object> primaryKeys = index.get(value);

        if (primaryKeys == null) {
            return;
        }

        primaryKeys.remove(primaryKey);

        if (primaryKeys.isEmpty()) {
            index.remove(value);
        }
    }

    /**
     * Нормализует название таблицы или столбца.
     *
     * @param name исходное название
     * @return нормализованное название
     */
    private String normalize(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Название таблицы или столбца не задано"
            );
        }

        return name.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Составной ключ для определения индекса.
     *
     * @param tableName название таблицы
     * @param columnName название столбца
     */
    private record IndexKey(String tableName, String columnName) {
    }
}