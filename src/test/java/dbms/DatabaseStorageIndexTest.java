package dbms;

import dbms.engine.Database;
import dbms.index.IndexManager;
import dbms.model.Column;
import dbms.model.DataType;
import dbms.model.Row;
import dbms.model.Table;
import dbms.model.TableSchema;
import dbms.storage.DatabaseStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseStorageIndexTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldSaveAndRestoreIndexes() throws IOException {
        TableSchema schema = new TableSchema(
                "subscribers",
                List.of(
                        new Column("subscriber_id", DataType.INTEGER, true, false),
                        new Column("phone_number", DataType.STRING, false, false)
                )
        );

        Table table = new Table(schema);

        table.addRow(new Row(schema, Map.of(
                "subscriber_id", 101,
                "phone_number", "+79990000001"
        )));

        table.addRow(new Row(schema, Map.of(
                "subscriber_id", 102,
                "phone_number", "+79990000002"
        )));

        Database database = new Database("telecom");
        database.addTable(table);
        database.createIndex("subscribers", "phone_number");

        DatabaseStorage storage = new DatabaseStorage();
        Path databaseFile = tempDir.resolve("telecom.db");
        Path indexFile = tempDir.resolve("telecom.db.idx");

        storage.save(database, databaseFile);

        assertTrue(Files.exists(databaseFile));
        assertTrue(Files.exists(indexFile));

        Database loadedDatabase = storage.load(databaseFile);
        IndexManager indexes = loadedDatabase.getIndexManager();

        assertTrue(indexes.hasIndex("subscribers", "phone_number"));

        assertEquals(
                Set.of(101),
                indexes.findEqual(
                        "subscribers",
                        "phone_number",
                        "+79990000001"
                )
        );
    }
}