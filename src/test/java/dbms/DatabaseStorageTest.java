package dbms;

import dbms.engine.Database;
import dbms.model.Column;
import dbms.model.DataType;
import dbms.model.ForeignKey;
import dbms.model.Row;
import dbms.model.Table;
import dbms.model.TableSchema;
import dbms.storage.DatabaseStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldSaveAndLoadDatabase() throws IOException {
        Database database = createDatabase();
        DatabaseStorage storage = new DatabaseStorage();

        Path file = tempDir.resolve("telecom.db");

        storage.save(database, file);
        Database restored = storage.load(file);

        assertEquals("telecom", restored.getName());
        assertEquals(2, restored.getTables().size());
        assertEquals(1, restored.getForeignKeys().size());

        Table subscribers = restored.getTable("subscribers");
        Table payments = restored.getTable("payments");

        assertEquals(1, subscribers.size());
        assertEquals(1, payments.size());

        Row subscriber = subscribers.getRows().get(0);

        assertEquals(101, subscriber.getValue("subscriber_id"));
        assertEquals("Иванов Иван Иванович",
                subscriber.getValue("full_name"));
        assertEquals("79991234567",
                subscriber.getValue("phone_number"));
        assertEquals(LocalDate.of(2026, 11, 1),
                subscriber.getValue("registration_date"));
        assertEquals(350.75, subscriber.getValue("balance"));
        assertEquals(true, subscriber.getValue("is_active"));

        Row payment = payments.getRows().get(0);

        assertEquals(1, payment.getValue("payment_id"));
        assertEquals(101, payment.getValue("subscriber_id"));
        assertEquals(500.0, payment.getValue("amount"));
        assertNull(payment.getValue("comment"));
        assertEquals(true, payment.getValue("is_confirmed"));
    }

    @Test
    void shouldRejectInvalidDatabaseFile() throws IOException {
        DatabaseStorage storage = new DatabaseStorage();
        Path file = tempDir.resolve("invalid.db");

        Files.write(file, new byte[]{0, 0, 0, 0});

        assertThrows(
                IOException.class,
                () -> storage.load(file)
        );
    }

    private Database createDatabase() {
        Database database = new Database("telecom");

        TableSchema subscriberSchema = new TableSchema(
                "subscribers",
                List.of(
                        new Column("subscriber_id", DataType.INTEGER, true, false),
                        new Column("full_name", DataType.STRING, false, false),
                        new Column("phone_number", DataType.STRING, false, false),
                        new Column("registration_date", DataType.DATE, false, false),
                        new Column("balance", DataType.DOUBLE, false, false),
                        new Column("is_active", DataType.BOOLEAN, false, false)
                )
        );

        TableSchema paymentSchema = new TableSchema(
                "payments",
                List.of(
                        new Column("payment_id", DataType.INTEGER, true, false),
                        new Column("subscriber_id", DataType.INTEGER, false, false),
                        new Column("payment_date", DataType.DATE, false, false),
                        new Column("amount", DataType.DOUBLE, false, false),
                        new Column("comment", DataType.STRING, false, true),
                        new Column("is_confirmed", DataType.BOOLEAN, false, false)
                )
        );

        database.addTable(new Table(subscriberSchema));
        database.addTable(new Table(paymentSchema));

        Map<String, Object> subscriberValues = new LinkedHashMap<>();
        subscriberValues.put("subscriber_id", 101);
        subscriberValues.put("full_name", "Иванов Иван Иванович");
        subscriberValues.put("phone_number", "79991234567");
        subscriberValues.put("registration_date", LocalDate.of(2026, 11, 1));
        subscriberValues.put("balance", 350.75);
        subscriberValues.put("is_active", true);

        database.insertRow(
                "subscribers",
                new Row(subscriberSchema, subscriberValues)
        );

        Map<String, Object> paymentValues = new LinkedHashMap<>();
        paymentValues.put("payment_id", 1);
        paymentValues.put("subscriber_id", 101);
        paymentValues.put("payment_date", LocalDate.of(2026, 11, 10));
        paymentValues.put("amount", 500.0);
        paymentValues.put("comment", null);
        paymentValues.put("is_confirmed", true);

        database.insertRow(
                "payments",
                new Row(paymentSchema, paymentValues)
        );

        database.addForeignKey(
                new ForeignKey(
                        "payments",
                        "subscriber_id",
                        "subscribers",
                        "subscriber_id"
                )
        );

        return database;
    }
}