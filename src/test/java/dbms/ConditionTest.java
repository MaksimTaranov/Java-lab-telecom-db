package dbms;

import dbms.engine.Condition;
import dbms.model.Column;
import dbms.model.DataType;
import dbms.model.Row;
import dbms.model.TableSchema;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConditionTest {

    @Test
    void shouldReturnFalseWhenActualValueIsNull() {
        TableSchema schema = new TableSchema(
                "payments",
                List.of(
                        new Column("payment_id", DataType.INTEGER, true, false),
                        new Column("comment", DataType.STRING, false, true)
                )
        );

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("payment_id", 1);
        values.put("comment", null);

        Row row = new Row(schema, values);

        for (Condition.Operator operator : Condition.Operator.values()) {
            Condition condition = new Condition(
                    "comment",
                    operator,
                    "Пополнение"
            );

            assertFalse(
                    condition.matches(row),
                    "Проверка не должна проходить для null: " + operator
            );
        }
    }
}