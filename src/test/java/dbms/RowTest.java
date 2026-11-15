package dbms;

import dbms.model.Column;
import dbms.model.DataType;
import dbms.model.Row;
import dbms.model.TableSchema;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RowTest {

    @Test
    void shouldRejectDuplicateColumnNamesIgnoringCase() {
        TableSchema schema = new TableSchema(
                "subscribers",
                List.of(
                        new Column("subscriber_id", DataType.INTEGER, true, false),
                        new Column("full_name", DataType.STRING, false, false)
                )
        );

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("subscriber_id", 1);
        values.put("full_name", "Иванов");
        values.put("FULL_NAME", "Петров");

        assertThrows(
                IllegalArgumentException.class,
                () -> new Row(schema, values)
        );
    }

    @Test
    void shouldAllowColumnNamesWithDifferentCase() {
        TableSchema schema = new TableSchema(
                "subscribers",
                List.of(
                        new Column("subscriber_id", DataType.INTEGER, true, false),
                        new Column("full_name", DataType.STRING, false, false)
                )
        );

        Row row = new Row(
                schema,
                Map.of(
                        "SUBSCRIBER_ID", 1,
                        "FULL_NAME", "Иванов"
                )
        );

        assertEquals(1, row.getValue("subscriber_id"));
        assertEquals("Иванов", row.getValue("full_name"));
    }
}