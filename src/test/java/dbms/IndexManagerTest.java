package dbms;

import dbms.engine.Database;
import dbms.index.IndexManager;
import dbms.model.Column;
import dbms.model.DataType;
import dbms.model.ForeignKey;
import dbms.model.Row;
import dbms.model.Table;
import dbms.model.TableSchema;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IndexManagerTest {

    private Database database;
    private Table subscribers;
    private Table payments;
    private IndexManager indexManager;

    @BeforeEach
    void setUp() {
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
                        new Column("comment", DataType.STRING, false, false),
                        new Column("is_confirmed", DataType.BOOLEAN, false, false)
                )
        );

        subscribers = new Table(subscriberSchema);
        payments = new Table(paymentSchema);

        database = new Database("telecom");
        database.addTable(subscribers);
        database.addTable(payments);

        database.addForeignKey(
                new ForeignKey(
                        "payments",
                        "subscriber_id",
                        "subscribers",
                        "subscriber_id"
                )
        );

        indexManager = database.getIndexManager();
    }

    @Test
    void shouldCreateIndexAndFindExistingValue() {
        database.insertRow("subscribers", createSubscriber(1, "Иванов"));
        database.insertRow("subscribers", createSubscriber(2, "Петров"));

        database.createIndex("subscribers", "full_name");

        assertTrue(indexManager.hasIndex("subscribers", "full_name"));
        assertEquals(
                Set.of(1),
                indexManager.findEqual("subscribers", "full_name", "Иванов")
        );
    }

    @Test
    void shouldUpdateIndexAfterInsert() {
        database.createIndex("subscribers", "full_name");

        database.insertRow("subscribers", createSubscriber(1, "Иванов"));

        assertEquals(
                Set.of(1),
                indexManager.findEqual("subscribers", "full_name", "Иванов")
        );
    }

    @Test
    void shouldUpdateIndexAfterValueChange() {
        Row subscriber = createSubscriber(1, "Иванов");
        database.insertRow("subscribers", subscriber);
        database.createIndex("subscribers", "full_name");

        String oldValue = (String) subscriber.getValue("full_name");
        String newValue = "Сидоров";

        subscriber.setValue("full_name", newValue);
        indexManager.onUpdate(
                subscribers,
                subscriber,
                "full_name",
                oldValue,
                newValue
        );

        assertTrue(
                indexManager.findEqual(
                        "subscribers", "full_name", oldValue
                ).isEmpty()
        );

        assertEquals(
                Set.of(1),
                indexManager.findEqual(
                        "subscribers", "full_name", newValue
                )
        );
    }

    @Test
    void shouldUpdateIndexAfterDelete() {
        database.insertRow("subscribers", createSubscriber(1, "Иванов"));
        database.createIndex("subscribers", "full_name");

        database.deleteRow("subscribers", 1, false);

        assertTrue(
                indexManager.findEqual(
                        "subscribers", "full_name", "Иванов"
                ).isEmpty()
        );
    }

    @Test
    void shouldUpdateIndexesAfterCascadeDelete() {
        database.insertRow("subscribers", createSubscriber(1, "Иванов"));
        database.insertRow("payments", createPayment(101, 1));

        database.createIndex("subscribers", "full_name");
        database.createIndex("payments", "subscriber_id");

        database.deleteRow("subscribers", 1, true);

        assertTrue(
                indexManager.findEqual(
                        "subscribers", "full_name", "Иванов"
                ).isEmpty()
        );

        assertTrue(
                indexManager.findEqual(
                        "payments", "subscriber_id", 1
                ).isEmpty()
        );
    }

    private Row createSubscriber(int id, String fullName) {
        return new Row(
                subscribers.getSchema(),
                Map.of(
                        "subscriber_id", id,
                        "full_name", fullName,
                        "phone_number", "+79991234567",
                        "registration_date", LocalDate.of(2026, 11, 11),
                        "balance", 500.0,
                        "is_active", true
                )
        );
    }

    private Row createPayment(int paymentId, int subscriberId) {
        return new Row(
                payments.getSchema(),
                Map.of(
                        "payment_id", paymentId,
                        "subscriber_id", subscriberId,
                        "payment_date", LocalDate.of(2026, 11, 11),
                        "amount", 300.0,
                        "comment", "Пополнение баланса",
                        "is_confirmed", true
                )
        );
    }
}