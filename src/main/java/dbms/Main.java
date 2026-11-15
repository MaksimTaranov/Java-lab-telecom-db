package dbms;

import dbms.engine.Condition;
import dbms.engine.Database;
import dbms.engine.QueryService;
import dbms.model.Column;
import dbms.model.Row;
import dbms.model.Table;
import dbms.model.TableSchema;
import dbms.sql.*;
import dbms.storage.DatabaseStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Запускает консольную СУБД TelecomDB.
 */
public class Main {
    /**
     * Загружает базу или создаёт новую, читает команды из консоли и сохраняет базу при обычном завершении.
     *
     * @param args аргументы командной строки, которые программа не использует
     */
    public static void main(String[] args){
        Path databaseFile = Path.of("data", "telecom.db");
        DatabaseStorage storage = new DatabaseStorage();
        Database database;
        try {
            if (Files.notExists(databaseFile)) {
                database = TelecomSchema.createDatabase();
                System.out.println("Создана новая база данных");
            } else {
                database = storage.load(databaseFile);
                System.out.println("База данных загружена");
            }
        } catch (IOException e) {
            System.out.println("Ошибка загрузки базы данных: " + e.getMessage());
            return;
        }

        Scanner sc = new Scanner(System.in);
        SqlParser parser = new SqlParser();
        QueryService qs = new QueryService(database);

        while (true) {
            try {
                System.out.print("telecom> ");
                if (!sc.hasNextLine()) {
                    break;
                }

                String command = sc.nextLine().trim();
                if (command.isEmpty()) {
                    continue;
                }
                if (command.equalsIgnoreCase("EXIT") || command.equalsIgnoreCase("EXIT;")) {
                    break;
                }

                String firstWord = command.split("\\s+", 2)[0];
                if (firstWord.equalsIgnoreCase("INSERT")) {
                    InsertCmd insertCmd = parser.parseInsert(command);
                    String tableName = insertCmd.getTableName();
                    Table table = database.getTable(tableName);
                    List<Column> columns = table.getSchema().getColumns();
                    List<String> columnNames = insertCmd.getColumnNames();

                    if (columnNames.isEmpty()) {
                        columnNames = new ArrayList<>();
                        for (Column column : table.getSchema().getColumns()) {
                            columnNames.add(column.getName());
                        }
                    }

                    List<Row> newRows = new ArrayList<>();
                    for (List<String> values : insertCmd.getRows()) {
                        if (values.size() != columnNames.size()) {
                            throw new IllegalArgumentException(
                                    "В строке " + (newRows.size() + 1)
                                            + " ожидалось значений: " + columnNames.size()
                                            + ", передано: " + values.size()
                            );
                        }

                        Map<String, Object> rowValues = new LinkedHashMap<>();
                        for (int i = 0; i < columnNames.size(); i++) {
                            String columnName = table.getSchema().getColumn(columnNames.get(i)).getName();
                            String value = values.get(i);

                            if (rowValues.containsKey(columnName)) {
                                throw new IllegalArgumentException("Столбец указан дважды: " + columnName);
                            }

                            if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
                                rowValues.put(columnName, value.substring(1, value.length() - 1));
                            }
                            else if (value.equalsIgnoreCase("NULL")) {
                                rowValues.put(columnName, null);
                            }
                            else {
                                rowValues.put(columnName, value);
                            }
                        }
                        Row newRow = new Row(table.getSchema(), rowValues);
                        newRows.add(newRow);
                    }
                    database.insertRows(tableName, newRows);
                    System.out.println("Добавлено строк: " + newRows.size());
                }
                else if (firstWord.equalsIgnoreCase("SELECT")){
                    SelectCmd selectCmd = parser.parseSelect(command);
                    Table table = database.getTable(selectCmd.getTableName());
                    List<String> columnNames = selectCmd.getColumnNames();
                    List<Column> columns = new ArrayList<>();
                    if (columnNames.isEmpty()){
                        columns = table.getSchema().getColumns();
                    }
                    else {
                        for (String name : columnNames){
                            columns.add(table.getSchema().getColumn(name));
                        }
                    }

                    Condition condition = parser.parseCondition(selectCmd.getWhereClause());
                    List<Row> rows = qs.selectRows(selectCmd.getTableName(), condition);
                    for (Column col : columns){
                        System.out.print(col.getName() + " | ");
                    }
                    System.out.println();

                    for (Row row : rows){
                        for (Column col : columns) {
                            if (row.getValue(col.getName()) == null){
                                System.out.print("NULL" + " | ");
                            }
                            else{
                                System.out.print(row.getValue(col.getName()) + " | ");
                            }
                        }
                        System.out.println();
                    }
                    System.out.println("Строк найдено: " + rows.size());
                }
                else if (firstWord.equalsIgnoreCase("DELETE")){
                    DeleteCmd deleteCmd = parser.parseDelete(command);
                    Condition condition = parser.parseCondition(deleteCmd.getWhereClause());
                    int deleted = qs.deleteRows(deleteCmd.getTableName(), condition, false);
                    System.out.println("Удалено строк: " + deleted);
                }
                else if (firstWord.equalsIgnoreCase("UPDATE")) {
                    UpdateCmd updateCmd = parser.parseUpdate(command);
                    Condition condition = parser.parseCondition(updateCmd.getWhereClause());
                    int updated = qs.updateRows(updateCmd.getTableName(), condition, updateCmd.getNewValues());
                    System.out.println("Изменено строк: " + updated);
                }
                else if (firstWord.equalsIgnoreCase("CREATE")) {
                    String[] words = command.split("\\s+", 3);
                    if (words.length >= 2 && words[1].equalsIgnoreCase("TABLE")) {
                        TableSchema schema = parser.parseCreateTable(command);
                        database.addTable(new Table(schema));
                        System.out.println("Таблица создана");
                    }
                    else if (words.length >= 2 && words[1].equalsIgnoreCase("INDEX")) {
                        CreateIndexCmd indexCmd = parser.parseCreateIndex(command);
                        database.createIndex(indexCmd.getTableName(), indexCmd.getColumnName());
                        System.out.println("Индекс создан");
                    }
                    else {
                        throw new IllegalArgumentException("Ожидается CREATE TABLE или CREATE INDEX");
                    }
                }
                else if (firstWord.equalsIgnoreCase("DROP")) {
                    String tableName = parser.parseDropTable(command);
                    database.dropTable(tableName);
                    System.out.println("Таблица удалена");
                }
                else {
                    System.out.println("Неизвестная команда: " + firstWord);
                }
            }
            catch (IllegalArgumentException | IllegalStateException e){
                System.out.println(e.getMessage());
            }
        }

        try {
            storage.save(database, databaseFile);
            System.out.println("База данных сохранена");
        } catch (IOException e) {
            System.out.println("Ошибка сохранения базы данных: " + e.getMessage());
        }
    }
}
