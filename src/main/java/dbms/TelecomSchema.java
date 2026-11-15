package dbms;

import dbms.engine.Database;
import dbms.model.*;

import java.util.List;

/**
 * Описывает начальную структуру базы данных оператора связи.
 */
public class TelecomSchema {
    /**
     * Создаёт пустую базу данных telecom с таблицами
     * subscribers и payments.
     * Внешний ключ payments.subscriber_id ссылается на
     * subscribers.subscriber_id, поэтому у одного абонента
     * может быть несколько платежей.
     *
     * @return новая база данных с двумя связанными таблицами
     */
    public static Database createDatabase(){
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

        Table subscribers = new Table(subscriberSchema);
        Table payments = new Table(paymentSchema);

        Database database = new Database("telecom");
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
        return database;
    }
}

