package dbms.storage;

import dbms.engine.Database;
import dbms.model.Column;
import dbms.model.DataType;
import dbms.model.ForeignKey;
import dbms.model.Row;
import dbms.model.Table;
import dbms.model.TableSchema;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Сохраняет базу данных и её индексы в файлы,
 * а также восстанавливает их при загрузке.
 */
public class DatabaseStorage {

    private static final int MAGIC = 0x44424D53;
    private static final int VERSION = 1;
    private static final int MAX_ELEMENTS = 1_000_000;

    /**
     * Сохраняет базу данных и описания её индексов.
     *
     * @param database сохраняемая база данных
     * @param file путь к файлу базы данных
     * @throws IOException если произошла ошибка записи
     */
    public void save(Database database, Path file) throws IOException {
        if (database == null || file == null) {
            throw new IllegalArgumentException(
                    "База данных и путь к файлу должны быть заданы"
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
                output.writeUTF(database.getName());

                output.writeInt(database.getTables().size());

                for (Table table : database.getTables().values()) {
                    writeTable(output, table);
                }

                output.writeInt(database.getForeignKeys().size());

                for (ForeignKey foreignKey : database.getForeignKeys()) {
                    output.writeUTF(foreignKey.getTableName());
                    output.writeUTF(foreignKey.getColumnName());
                    output.writeUTF(foreignKey.getReferencedTableName());
                    output.writeUTF(foreignKey.getReferencedColumnName());
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

        database.getIndexManager().save(getIndexFile(absolutePath));
    }

    /**
     * Загружает базу данных и восстанавливает её индексы,
     * если файл индексов существует.
     *
     * @param file путь к файлу базы данных
     * @return восстановленная база данных
     * @throws IOException если файл повреждён или не может быть прочитан
     */
    public Database load(Path file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("Путь к файлу не задан");
        }

        Database database;

        try (DataInputStream input = new DataInputStream(
                Files.newInputStream(file)
        )) {
            int magic = input.readInt();

            if (magic != MAGIC) {
                throw new IOException("Файл не является базой данных DBMS");
            }

            int version = input.readInt();

            if (version != VERSION) {
                throw new IOException(
                        "Неподдерживаемая версия файла: " + version
                );
            }

            String databaseName = input.readUTF();
            database = new Database(databaseName);

            int tableCount = readCount(input);

            for (int i = 0; i < tableCount; i++) {
                readTable(input, database);
            }

            int foreignKeyCount = readCount(input);

            for (int i = 0; i < foreignKeyCount; i++) {
                ForeignKey foreignKey = new ForeignKey(
                        input.readUTF(),
                        input.readUTF(),
                        input.readUTF(),
                        input.readUTF()
                );

                database.addForeignKey(foreignKey);
            }

            if (input.read() != -1) {
                throw new IOException(
                        "В файле обнаружены лишние данные"
                );
            }

        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new IOException(
                    "Не удалось восстановить базу данных: файл повреждён",
                    e
            );
        }

        Path indexFile = getIndexFile(file.toAbsolutePath());

        if (Files.exists(indexFile)) {
            database.getIndexManager().load(indexFile, database);
        }

        return database;
    }

    /**
     * Формирует путь к файлу индексов.
     *
     * @param databaseFile путь к файлу базы данных
     * @return путь к файлу индексов
     */
    private Path getIndexFile(Path databaseFile) {
        return databaseFile.resolveSibling(
                databaseFile.getFileName() + ".idx"
        );
    }

    private void writeTable(
            DataOutputStream output,
            Table table
    ) throws IOException {
        TableSchema schema = table.getSchema();

        output.writeUTF(schema.getTableName());
        output.writeInt(schema.getColumns().size());

        for (Column column : schema.getColumns()) {
            output.writeUTF(column.getName());
            output.writeUTF(column.getType().name());
            output.writeBoolean(column.isPrimaryKey());
            output.writeBoolean(column.isNullable());
        }

        output.writeInt(table.size());

        for (Row row : table.getRows()) {
            for (Column column : schema.getColumns()) {
                writeValue(
                        output,
                        column.getType(),
                        row.getValue(column.getName())
                );
            }
        }
    }

    private void readTable(
            DataInputStream input,
            Database database
    ) throws IOException {
        String tableName = input.readUTF();
        int columnCount = readCount(input);

        if (columnCount == 0) {
            throw new IOException("Таблица не содержит столбцов");
        }

        List<Column> columns = new ArrayList<>();

        for (int i = 0; i < columnCount; i++) {
            String columnName = input.readUTF();
            DataType type;

            try {
                type = DataType.valueOf(input.readUTF());
            } catch (IllegalArgumentException e) {
                throw new IOException("Неизвестный тип данных", e);
            }

            boolean primaryKey = input.readBoolean();
            boolean nullable = input.readBoolean();

            columns.add(
                    new Column(columnName, type, primaryKey, nullable)
            );
        }

        TableSchema schema = new TableSchema(tableName, columns);
        Table table = new Table(schema);

        database.addTable(table);

        int rowCount = readCount(input);

        for (int i = 0; i < rowCount; i++) {
            Map<String, Object> values = new LinkedHashMap<>();

            for (Column column : columns) {
                values.put(
                        column.getName(),
                        readValue(input, column.getType())
                );
            }

            database.insertRow(
                    tableName,
                    new Row(schema, values)
            );
        }
    }

    private void writeValue(
            DataOutputStream output,
            DataType type,
            Object value
    ) throws IOException {
        output.writeBoolean(value != null);

        if (value == null) {
            return;
        }

        switch (type) {
            case INTEGER -> output.writeInt((Integer) value);
            case DOUBLE -> output.writeDouble((Double) value);
            case STRING -> output.writeUTF((String) value);
            case DATE -> output.writeLong(
                    ((java.time.LocalDate) value).toEpochDay()
            );
            case BOOLEAN -> output.writeBoolean((Boolean) value);
        }
    }

    private Object readValue(
            DataInputStream input,
            DataType type
    ) throws IOException {
        if (!input.readBoolean()) {
            return null;
        }

        return switch (type) {
            case INTEGER -> input.readInt();
            case DOUBLE -> input.readDouble();
            case STRING -> input.readUTF();
            case DATE -> java.time.LocalDate.ofEpochDay(input.readLong());
            case BOOLEAN -> input.readBoolean();
        };
    }

    private int readCount(DataInputStream input) throws IOException {
        int count = input.readInt();

        if (count < 0 || count > MAX_ELEMENTS) {
            throw new IOException(
                    "Некорректное количество элементов в файле: " + count
            );
        }

        return count;
    }
}