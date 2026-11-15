package dbms;

import dbms.engine.Database;
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

import static org.junit.jupiter.api.Assertions.*;

class DatabaseTest {
    private Database database;
    private Table subscribers;
    private Table payments;

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
    }

    @Test
    void shouldInsertSubscriber() {
        database.insertRow("subscribers", createSubscriber(1));

        assertEquals(1, subscribers.size());
        assertNotNull(subscribers.getRow(1));
    }

    @Test
    void shouldRejectDuplicatePrimaryKey() {
        database.insertRow("subscribers", createSubscriber(1));

        assertThrows(
                IllegalArgumentException.class,
                () -> database.insertRow("subscribers", createSubscriber(1))
        );

        assertEquals(1, subscribers.size());
    }

    @Test
    void shouldRejectPaymentWithUnknownSubscriber() {
        assertThrows(
                IllegalArgumentException.class,
                () -> database.insertRow("payments", createPayment(101, 999))
        );

        assertTrue(payments.isEmpty());
    }

    @Test
    void shouldRejectDeletionWithoutCascadeConfirmation() {
        database.insertRow("subscribers", createSubscriber(1));
        database.insertRow("payments", createPayment(101, 1));

        assertThrows(
                IllegalStateException.class,
                () -> database.deleteRow("subscribers", 1, false)
        );

        assertNotNull(subscribers.getRow(1));
        assertNotNull(payments.getRow(101));
    }

    @Test
    void shouldDeleteSubscriberWithPayments() {
        database.insertRow("subscribers", createSubscriber(1));
        database.insertRow("payments", createPayment(101, 1));
        database.insertRow("payments", createPayment(102, 1));

        boolean deleted = database.deleteRow("subscribers", 1, true);

        assertTrue(deleted);
        assertNull(subscribers.getRow(1));
        assertTrue(payments.isEmpty());
    }

    private Row createSubscriber(int id) {
        return new Row(
                subscribers.getSchema(),
                Map.of(
                        "subscriber_id", id,
                        "full_name", "Иванов Иван Иванович",
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
