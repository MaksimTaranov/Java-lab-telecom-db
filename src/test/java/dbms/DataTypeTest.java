package dbms;

import dbms.model.DataType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DataTypeTest {

    @Test
    void shouldPreserveSpacesInString() {
        String value = "  Иванов Иван  ";

        assertEquals(
                value,
                DataType.STRING.convert(value)
        );
    }

    @Test
    void shouldTrimSpacesBeforeNumericConversion() {
        assertEquals(
                125,
                DataType.INTEGER.convert(" 125 ")
        );
    }

    @Test
    void shouldConvertBooleanIgnoringCase() {
        assertEquals(
                true,
                DataType.BOOLEAN.convert(" TRUE ")
        );
    }
}