package dbms;

import dbms.engine.Condition;
import dbms.engine.Database;
import dbms.engine.QueryService;
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

class QueryServiceDeleteTest {

    private Database database;
    private QueryService queryService;

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

        database = new Database("telecom");

        database.addTable(new Table(subscriberSchema));
        database.addTable(new Table(paymentSchema));

        database.addForeignKey(
                new ForeignKey(
                        "payments",
                        "subscriber_id",
                        "subscribers",
                        "subscriber_id"
                )
        );

        database.insertRow(
                "subscribers",
                createSubscriber(subscriberSchema, 1, 300.0)
        );

        database.insertRow(
                "subscribers",
                createSubscriber(subscriberSchema, 2, 700.0)
        );

        database.insertRow(
                "subscribers",
                createSubscriber(subscriberSchema, 3, 1000.0)
        );

        database.insertRow(
                "payments",
                createPayment(paymentSchema, 101, 1, 500.0)
        );

        database.insertRow(
                "payments",
                createPayment(paymentSchema, 102, 1, 250.0)
        );

        queryService = new QueryService(database);
    }

    @Test
    void shouldDeleteRowsMatchingCondition() {
        Condition condition = new Condition(
                "balance",
                Condition.Operator.LESS,
                800.0
        );

        int deleted = queryService.deleteRows(
                "subscribers",
                condition,
                true
        );

        assertEquals(2, deleted);
        assertNull(database.getTable("subscribers").getRow(1));
        assertNull(database.getTable("subscribers").getRow(2));
        assertNotNull(database.getTable("subscribers").getRow(3));

        assertTrue(database.getTable("payments").isEmpty());
    }

    @Test
    void shouldRejectDeletingSubscriberWithPaymentsWithoutCascade() {
        assertThrows(
                IllegalStateException.class,
                () -> queryService.deleteRows(
                        "subscribers",
                        new Condition(
                                "subscriber_id",
                                Condition.Operator.EQUALS,
                                1
                        ),
                        false
                )
        );

        assertNotNull(database.getTable("subscribers").getRow(1));
        assertEquals(2, database.getTable("payments").size());
    }

    @Test
    void shouldDeleteSubscriberAndPaymentsWithCascade() {
        int deleted = queryService.deleteRows(
                "subscribers",
                new Condition(
                        "subscriber_id",
                        Condition.Operator.EQUALS,
                        1
                ),
                true
        );

        assertEquals(1, deleted);
        assertNull(database.getTable("subscribers").getRow(1));
        assertTrue(database.getTable("payments").isEmpty());
    }

    @Test
    void shouldDeletePaymentWithoutDeletingSubscriber() {
        int deleted = queryService.deleteRows(
                "payments",
                new Condition(
                        "payment_id",
                        Condition.Operator.EQUALS,
                        101
                ),
                false
        );

        assertEquals(1, deleted);
        assertNull(database.getTable("payments").getRow(101));
        assertNotNull(database.getTable("subscribers").getRow(1));
        assertEquals(1, database.getTable("payments").size());
    }

    @Test
    void shouldReturnZeroWhenNoRowsMatch() {
        int deleted = queryService.deleteRows(
                "subscribers",
                new Condition(
                        "balance",
                        Condition.Operator.GREATER,
                        2000.0
                ),
                true
        );

        assertEquals(0, deleted);
        assertEquals(3, database.getTable("subscribers").size());
    }

    private Row createSubscriber(
            TableSchema schema,
            int id,
            double balance
    ) {
        return new Row(
                schema,
                Map.of(
                        "subscriber_id", id,
                        "full_name", "Иванов Иван Иванович",
                        "phone_number", "+79991234567",
                        "registration_date", LocalDate.of(2026, 11, 11),
                        "balance", balance,
                        "is_active", true
                )
        );
    }

    private Row createPayment(
            TableSchema schema,
            int paymentId,
            int subscriberId,
            double amount
    ) {
        return new Row(
                schema,
                Map.of(
                        "payment_id", paymentId,
                        "subscriber_id", subscriberId,
                        "payment_date", LocalDate.of(2026, 11, 11),
                        "amount", amount,
                        "comment", "Пополнение счёта",
                        "is_confirmed", true
                )
        );
    }
}