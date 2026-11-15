package dbms.sql;

import dbms.engine.Condition;
import dbms.model.Column;
import dbms.model.DataType;
import dbms.model.TableSchema;

import java.util.*;

/**
 * Преобразует текст SQL-подобных команд в объекты для выполнения.
 */
public class SqlParser {
    /**
     * Разбирает запрос выборки со списком столбцов или символом * и необязательным условием.
     *
     * @param command текст команды SELECT
     * @return имя таблицы, список столбцов и текст условия
     * @throws IllegalArgumentException если структура команды некорректна
     */
    public SelectCmd parseSelect(String command){
        command = command.trim();
        if (command.endsWith(";")){
            command = command.substring(0, command.length() - 1);
            command = command.trim();
        }

        String[] header = command.split("\\s+", 2);
        if (header.length != 2 || !header[0].equalsIgnoreCase("SELECT")) {
            throw new IllegalArgumentException("Ожидается команда SELECT");
        }

        String[] parts = header[1].split("(?i)\\s+FROM\\s+", 2);
        if (parts.length != 2 || parts[1].trim().isEmpty()) {
            throw new IllegalArgumentException("Ожидается SELECT имена_столбцов FROM имя_таблицы");
        }
        String beforeFrom = parts[0].trim();

        String[] tableAndWhere = parts[1].trim().split("(?i)\\s+WHERE(?:\\s+|$)", 2);
        String tableName = tableAndWhere[0].trim();
        String whereClause = null;

        if (tableAndWhere.length == 2) {
            whereClause = tableAndWhere[1].trim();

            if (whereClause.isEmpty()) {
                throw new IllegalArgumentException("После WHERE ожидается условие");
            }
        }
        if (tableName.isEmpty() || tableName.split("\\s+").length != 1) {
            throw new IllegalArgumentException("После FROM ожидается имя одной таблицы");
        }

        List<String> columnNames = new ArrayList<>();
        String[] columnArgs = beforeFrom.split(",", -1);
        if (beforeFrom.equals("*")) {
            return new SelectCmd(tableName, columnNames, whereClause);
        }
        for(String name : columnArgs){
            String columnName = name.trim();
            if (columnName.isEmpty()) {
                throw new IllegalArgumentException("Пропущено имя столбца");
            }
            if (columnName.equals("*") || columnName.split("\\s+").length != 1) {
                throw new IllegalArgumentException("Некорректное имя столбца: " + columnName);
            }
            columnNames.add(columnName);
        }

        return new SelectCmd(tableName, columnNames, whereClause);
    }

    /**
     * Разбирает вставку одной или нескольких строк с необязательным списком столбцов.
     *
     * @param command текст команды INSERT INTO
     * @return имя таблицы, список столбцов и значения по строкам
     * @throws IllegalArgumentException если структура команды или списка значений некорректна
     */
    public InsertCmd parseInsert(String command) {
        command = command.trim();
        if (command.endsWith(";")) {
            command = command.substring(0, command.length() - 1);
            command = command.trim();
        }

        String[] parts = command.split("(?i)\\bVALUES\\b", 2);
        if (parts.length != 2 || parts[1].trim().isEmpty()) {
            throw new IllegalArgumentException("Ожидается INSERT INTO имя_таблицы VALUES (...)");
        }
        String beforeValues = parts[0].trim();
        String afterValues = parts[1].trim();

        List<String> columnNames = new ArrayList<>();
        String header = beforeValues;
        int openBracket = beforeValues.indexOf('(');
        boolean inQuotes = false;
        if (openBracket != -1) {
            if (!beforeValues.endsWith(")")) {
                throw new IllegalArgumentException("Ожидается ) после списка столбцов");
            }


            header = beforeValues.substring(0, openBracket).trim();
            String columnsArgs = beforeValues.substring(openBracket + 1, beforeValues.length() - 1);

            for (String name : columnsArgs.split(",", -1)) {
                String columnName = name.trim();
                if (columnName.isEmpty()) {
                    throw new IllegalArgumentException("Пропущено имя столбца");
                }
                columnNames.add(columnName);
            }
        }

        String[] words = header.split("\\s+");
        if (words.length != 3
                || !words[0].equalsIgnoreCase("INSERT")
                || !words[1].equalsIgnoreCase("INTO")) {
            throw new IllegalArgumentException("Ожидается INSERT INTO имя_таблицы");
        }
        String tableName = words[2];

        if (!afterValues.startsWith("(") || !afterValues.endsWith(")")) {
            throw new IllegalArgumentException("Ожидаются значения в скобках после VALUES");
        }
        List<List<String>> rows = parseGroupsWithValue(afterValues);
        return new InsertCmd(tableName, columnNames, rows);
    }

    /**
     * Преобразует текст одного сравнения в условие для Condition.
     *
     * @param whereClause текст после WHERE или null, если условия нет
     * @return условие сравнения или null, если условие отсутствует
     * @throws IllegalArgumentException если отсутствует столбец, оператор или значение
     *          либо оператор не поддерживается
     */
    public Condition parseCondition(String whereClause) {
        if (whereClause == null) {
            return null;
        }
        String text = whereClause.trim();
        int i = 0;
        while (i < text.length() && "<>=!".indexOf(text.charAt(i)) == -1) {
            i++;
        }
        if (i == text.length()) {
            throw new IllegalArgumentException("В условии пропущен оператор сравнения");
        }
        String columnName = text.substring(0, i).trim();
        int operatorStart = i;
        i++;
        if (i < text.length() && text.charAt(i) == '=') {
            i++;
        }

        String operatorText = text.substring(operatorStart, i);
        String value = text.substring(i).trim();

        if (columnName.isEmpty() || value.isEmpty()) {
            throw new IllegalArgumentException("Ожидается условие вида столбец оператор значение");
        }

        Condition.Operator operator = switch (operatorText) {
            case "="  -> Condition.Operator.EQUALS;
            case "!=" -> Condition.Operator.NOT_EQUALS;
            case ">"  -> Condition.Operator.GREATER;
            case ">=" -> Condition.Operator.GREATER_OR_EQUALS;
            case "<"  -> Condition.Operator.LESS;
            case "<=" -> Condition.Operator.LESS_OR_EQUALS;
            default -> throw new IllegalArgumentException("Неизвестный оператор: " + operatorText);
        };

        boolean startsWithQuote = value.startsWith("'");
        boolean endsWithQuote = value.endsWith("'");
        if (startsWithQuote || endsWithQuote) {
            if (value.length() < 2 || !startsWithQuote || !endsWithQuote) {
                throw new IllegalArgumentException("Значение в WHERE нужно заключить в одинарные кавычки");
            }
            value = value.substring(1, value.length() - 1);
        }
        return new Condition(columnName, operator, value);
    }

    /**
     * Разбирает имя таблицы и необязательное условие удаления.
     *
     * @param command текст команды DELETE FROM
     * @return имя таблицы и текст условия
     * @throws IllegalArgumentException если структура команды некорректна
     */
    public DeleteCmd parseDelete(String command) {
        command = command.trim();
        if (command.endsWith(";")) {
            command = command.substring(0, command.length() - 1).trim();
        }

        String[] parts = command.split("\\s+", 3);
        if (parts.length != 3
                || !parts[0].equalsIgnoreCase("DELETE")
                || !parts[1].equalsIgnoreCase("FROM")) {
            throw new IllegalArgumentException("Ожидается DELETE FROM имя_таблицы");
        }

        String[] tableAndWhere = parts[2].split("(?i)\\s+WHERE(?:\\s+|$)", 2);
        String tableName = tableAndWhere[0].trim();
        if (tableName.isEmpty() || tableName.split("\\s+").length != 1) {
            throw new IllegalArgumentException("После FROM ожидается имя одной таблицы");
        }

        String whereClause = null;
        if (tableAndWhere.length == 2) {
            whereClause = tableAndWhere[1].trim();
            if (whereClause.isEmpty()) {
                throw new IllegalArgumentException("После WHERE ожидается условие");
            }
        }

        return new DeleteCmd(tableName, whereClause);
    }

    /**
     * Разбирает присваивания после SET и выделяет необязательное условие.
     *
     * @param command текст команды UPDATE
     * @return имя таблицы, новые значения столбцов и текст условия
     * @throws IllegalArgumentException если структура команды или присваиваний некорректна
     */
    public UpdateCmd parseUpdate(String command) {
        command = command.trim();
        if (command.endsWith(";")) {
            command = command.substring(0, command.length() - 1).trim();
        }

        String[] parts = command.split("\\s+", 4);
        if (parts.length != 4
                || !parts[0].equalsIgnoreCase("UPDATE")
                || !parts[2].equalsIgnoreCase("SET")) {
            throw new IllegalArgumentException("Ожидается UPDATE таблица SET столбец = значение");
        }

        String tableName = parts[1];
        String assignments = parts[3];
        String whereClause = null;
        int whereIndex = findWhere(assignments);
        if (whereIndex != -1) {
            whereClause = assignments.substring(whereIndex + 5).trim();
            assignments = assignments.substring(0, whereIndex).trim();

            if (whereClause.isEmpty()) {
                throw new IllegalArgumentException("После WHERE ожидается условие");
            }
        }

        Map<String, Object> newValues = new LinkedHashMap<>();
        for (String assignment : splitValues(assignments)) {
            String[] pair = assignment.split("=", 2);
            if (pair.length != 2 || pair[0].trim().isEmpty() || pair[1].trim().isEmpty()) {
                throw new IllegalArgumentException("После SET ожидается столбец = значение");
            }

            String columnName = pair[0].trim();
            String value = pair[1].trim();
            if (newValues.containsKey(columnName)) {
                throw new IllegalArgumentException("Столбец указан дважды: " + columnName);
            }
            if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
                newValues.put(columnName, value.substring(1, value.length() - 1));
            }
            else if (value.equalsIgnoreCase("NULL")) {
                newValues.put(columnName, null);
            }
            else {
                newValues.put(columnName, value);
            }
        }
        return new UpdateCmd(tableName, newValues, whereClause);
    }

    /**
     * Разбирает команду создания индекса по одному столбцу.
     *
     * @param command текст команды CREATE INDEX
     * @return имя таблицы и имя индексируемого столбца
     * @throws IllegalArgumentException если структура команды некорректна
     */
    public CreateIndexCmd parseCreateIndex(String command) {
        command = command.trim();
        if (command.endsWith(";")) {
            command = command.substring(0, command.length() - 1).trim();
        }

        int openBracket = command.indexOf('(');
        if (openBracket == -1 || !command.endsWith(")")) {
            throw new IllegalArgumentException("Ожидается имя столбца в скобках");
        }

        String header = command.substring(0, openBracket).trim();
        String[] words = header.split("\\s+");
        if (words.length != 5
                || !words[0].equalsIgnoreCase("CREATE")
                || !words[1].equalsIgnoreCase("INDEX")
                || !words[3].equalsIgnoreCase("ON")) {
            throw new IllegalArgumentException(
                    "Ожидается CREATE INDEX имя_индекса ON таблица (столбец)"
            );
        }

        String tableName = words[4];
        String columnName = command.substring(openBracket + 1, command.length() - 1).trim();
        if (columnName.isEmpty() || columnName.contains(",")
                || columnName.split("\\s+").length != 1) {
            throw new IllegalArgumentException("В скобках ожидается имя одного столбца");
        }
        return new CreateIndexCmd(tableName, columnName);
    }

    /**
     * Разбирает описания столбцов и создаёт схему новой таблицы.
     * Первичный ключ указывается после типа столбца.
     *
     * @param command текст команды CREATE TABLE
     * @return схема новой таблицы
     * @throws IllegalArgumentException если команда, тип столбца или схема некорректны
     */
    public TableSchema parseCreateTable(String command) {
        command = command.trim();
        if (command.endsWith(";")) {
            command = command.substring(0, command.length() - 1).trim();
        }

        int openBracket = command.indexOf('(');
        if (openBracket == -1 || !command.endsWith(")")) {
            throw new IllegalArgumentException("Ожидаются столбцы в скобках");
        }

        String[] header = command.substring(0, openBracket).trim().split("\\s+");
        if (header.length != 3
                || !header[0].equalsIgnoreCase("CREATE")
                || !header[1].equalsIgnoreCase("TABLE")) {
            throw new IllegalArgumentException("Ожидается CREATE TABLE имя_таблицы");
        }

        String tableName = header[2];
        String columnsText = command.substring(openBracket + 1, command.length() - 1);
        List<Column> columns = new ArrayList<>();
        for (String description : columnsText.split(",", -1)) {
            String[] words = description.trim().split("\\s+");
            boolean primaryKey = words.length == 4
                    && words[2].equalsIgnoreCase("PRIMARY")
                    && words[3].equalsIgnoreCase("KEY");

            if (words.length != 2 && !primaryKey) {
                throw new IllegalArgumentException("Некорректное описание столбца: " + description);
            }

            String columnName = words[0];
            DataType type = DataType.valueOf(words[1].toUpperCase(Locale.ROOT));
            columns.add(new Column(columnName, type, primaryKey, !primaryKey));
        }
        return new TableSchema(tableName, columns);
    }

    /**
     * Извлекает имя таблицы, которую нужно удалить.
     *
     * @param command текст команды DROP TABLE
     * @return имя удаляемой таблицы
     * @throws IllegalArgumentException если структура команды некорректна
     */
    public String parseDropTable(String command) {
        command = command.trim();
        if (command.endsWith(";")) {
            command = command.substring(0, command.length() - 1).trim();
        }

        String[] words = command.split("\\s+");
        if (words.length != 3
                || !words[0].equalsIgnoreCase("DROP")
                || !words[1].equalsIgnoreCase("TABLE")) {
            throw new IllegalArgumentException("Ожидается DROP TABLE имя_таблицы");
        }
        return words[2];
    }

    /**
     * Разбирает группы значений в скобках после VALUES.
     * Каждая группа соответствует одной строке таблицы.
     *
     * @param afterValues часть команды после VALUES
     * @return список строк, где каждая строка представлена списком исходных значений
     * @throws IllegalArgumentException если нарушен формат групп или их значений
     */
    private List<List<String>> parseGroupsWithValue(String afterValues){
        List<List<String>> rows = new ArrayList<>();
        int i = 0;
        while(true){
            while(i < afterValues.length() && Character.isWhitespace(afterValues.charAt(i))){
                i++;
            }
            if(i == afterValues.length() || afterValues.charAt(i) != '('){
                throw new IllegalArgumentException("Ожидается группа значений в скобках");
            }
            int start = i + 1;
            i = start;
            boolean inQuotes = false;

            while(i < afterValues.length()){
                char symbol = afterValues.charAt(i);
                if(symbol == '\''){
                    inQuotes = !inQuotes;
                }
                else if (!inQuotes && symbol == ')'){
                    break;
                }
                else if (!inQuotes && symbol == '('){
                    throw new IllegalArgumentException("Лишняя скобка внутри группы значений");
                }
                i++;
            }
            if(i == afterValues.length()){
                throw new IllegalArgumentException("Группа значений не закрыта");
            }

            String group = afterValues.substring(start, i);
            rows.add(splitValues(group));
            i++;
            while(i < afterValues.length() && Character.isWhitespace(afterValues.charAt(i))){
                i++;
            }
            if(i == afterValues.length()){
                return rows;
            }
            if (afterValues.charAt(i) != ','){
                throw new IllegalArgumentException("Должна быть запятая между группами значений");
            }
            i++;
        }
    }
    /**
     * Разделяет значения или присваивания по запятым вне одинарных кавычек.
     * Внешние пробелы удаляются, кавычки сохраняются.
     *
     * @param arguments текст списка значений или присваиваний
     * @return элементы списка в порядке записи
     * @throws IllegalArgumentException если элемент пуст или строка в кавычках не закрыта
     */
    private List<String> splitValues(String arguments){
        List<String> values = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for(int i = 0; i < arguments.length(); i++) {
            char symbol = arguments.charAt(i);
            if (symbol == '\''){
                inQuotes = !inQuotes;
                cur.append(symbol);
            }
            else if (!inQuotes && symbol == ','){
                String arg = cur.toString().trim();
                if (arg.isEmpty()){
                    throw new IllegalArgumentException("Были переданы пустые аргументы");
                }
                values.add(arg);
                cur.setLength(0);
            }
            else{
                cur.append(symbol);
            }
        }
        if (inQuotes){
            throw new IllegalArgumentException("Переданы неверные аргументы, не было закрывающей кавычки");
        }
        String arg = cur.toString().trim();
        if (arg.isEmpty()){
            throw new IllegalArgumentException("Были переданы пустые аргументы");
        }
        values.add(arg);
        return values;
    }

    /**
     * Находит ключевое слово WHERE вне одинарных кавычек.
     *
     * @param text часть команды после SET
     * @return индекс начала ключевого слова или -1, если оно отсутствует
     */
    private int findWhere(String text) {
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\'') {
                inQuotes = !inQuotes;
            }
            int end = i + 5;
            if (!inQuotes
                    && (i == 0 || Character.isWhitespace(text.charAt(i - 1)))
                    && end <= text.length()
                    && text.substring(i, end).equalsIgnoreCase("WHERE")
                    && (end == text.length() || Character.isWhitespace(text.charAt(end)))) {
                return i;
            }
        }
        return -1;
    }
}
