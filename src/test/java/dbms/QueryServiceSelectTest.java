package dbms;

import dbms.engine.Condition;
import dbms.engine.Database;
import dbms.engine.QueryService;
import dbms.model.Column;
import dbms.model.DataType;
import dbms.model.Row;
import dbms.model.Table;
import dbms.model.TableSchema;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QueryServiceSelectTest {

    private Database database;
    private QueryService queryService;

    @BeforeEach
    void setUp() {
        TableSchema schema = new TableSchema(
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

        Table subscribers = new Table(schema);

        database = new Database("telecom");
        database.addTable(subscribers);

        subscribers.addRow(createSubscriber(schema, 1, 300.0));
        subscribers.addRow(createSubscriber(schema, 2, 700.0));
        subscribers.addRow(createSubscriber(schema, 3, 1000.0));

        queryService = new QueryService(database);
    }

    @Test
    void shouldSelectAllRows() {
        List<Row> result = queryService.selectRows(
                "subscribers", null
        );

        assertEquals(3, result.size());
    }

    @Test
    void shouldSelectRowsByCondition() {
        Condition condition = new Condition(
                "balance",
                Condition.Operator.GREATER,
                500.0
        );

        List<Row> result = queryService.selectRows(
                "subscribers", condition
        );

        assertEquals(2, result.size());
    }

    @Test
    void shouldReturnEmptyListWhenNoRowsMatch() {
        Condition condition = new Condition(
                "balance",
                Condition.Operator.GREATER,
                2000.0
        );

        List<Row> result = queryService.selectRows(
                "subscribers", condition
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldSelectRowsUsingIndex() {
        database.createIndex("subscribers", "balance");

        Condition condition = new Condition(
                "balance",
                Condition.Operator.EQUALS,
                700.0
        );

        List<Row> result = queryService.selectRows(
                "subscribers", condition
        );

        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getValue("subscriber_id"));
    }

    @Test
    void shouldReturnEmptyListWhenIndexedValueDoesNotExist() {
        database.createIndex("subscribers", "balance");

        Condition condition = new Condition(
                "balance",
                Condition.Operator.EQUALS,
                500.0
        );

        List<Row> result = queryService.selectRows(
                "subscribers", condition
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldUseConditionWhenIndexExistsButOperatorIsNotEquals() {
        database.createIndex("subscribers", "balance");

        Condition condition = new Condition(
                "balance",
                Condition.Operator.GREATER,
                500.0
        );

        List<Row> result = queryService.selectRows(
                "subscribers", condition
        );

        assertEquals(2, result.size());
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
}