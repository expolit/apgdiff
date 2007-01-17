/*
 * $Id$
 */
package cz.startnet.utils.pgdiff.parsers;

import cz.startnet.utils.pgdiff.schema.PgColumn;
import cz.startnet.utils.pgdiff.schema.PgSchema;
import cz.startnet.utils.pgdiff.schema.PgTable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Parses CREATE TABLE commands.
 *
 * @author fordfrog
 * @version $Id$
 */
public class CreateTableParser {
    /**
     * Pattern for getting table name from CREATE TABLE.
     */
    private static final Pattern PATTERN_TABLE_NAME =
        Pattern.compile("CREATE TABLE ([^ ]+)[ ]*\\(");

    /**
     * Pattern for getting CONSTRAINT parameters.
     */
    private static final Pattern PATTERN_CONSTRAINT =
        Pattern.compile("CONSTRAINT ([^ ]+) (.*)");

    /**
     * Pattern for parsing column definition.
     */
    private static final Pattern PATTERN_COLUMN =
        Pattern.compile("([^ ]+) (.*)");

    /**
     * Pattern for parsing INHERITS.
     */
    private static final Pattern PATTERN_INHERITS =
        Pattern.compile("INHERITS ([^;]+)[;]?");

    /**
     * Creates a new instance of CreateTableParser.
     */
    private CreateTableParser() {
        super();
    }

    /**
     * Parses CREATE TABLE command.
     *
     * @param schema schema to be filled
     * @param command CREATE TABLE command
     *
     * @throws ParserException Thrown if problem occured while parsing DDL.
     */
    public static void parse(final PgSchema schema, final String command) {
        String line = command;
        final Matcher matcher = PATTERN_TABLE_NAME.matcher(line);
        final String tableName;

        if (matcher.find()) {
            tableName = matcher.group(1).trim();
            line =
                ParserUtils.removeSubString(
                        line,
                        matcher.start(),
                        matcher.end());
        } else {
            throw new ParserException(
                    ParserException.CANNOT_PARSE_COMMAND + line);
        }

        final PgTable table = schema.getTable(tableName);
        parseRows(table, ParserUtils.removeLastSemicolon(line));
    }

    /**
     * Parses COLUMN and other DDL within '(' and ')' in CREATE TABLE
     * definition.
     *
     * @param table table being parsed
     * @param line line being processed
     *
     * @throws ParserException Thrown if problem occured while parsing DDL.
     */
    private static void parseColumnDefs(final PgTable table, final String line) {
        if (line.length() > 0) {
            if (line.startsWith("CONSTRAINT ")) {
                final Matcher matcher = PATTERN_CONSTRAINT.matcher(line.trim());

                if (matcher.matches()) {
                    table.getConstraint(matcher.group(1).trim()).setDefinition(
                            matcher.group(2).trim());
                } else {
                    throw new ParserException(
                            ParserException.CANNOT_PARSE_COMMAND + line);
                }
            } else {
                final Matcher matcher = PATTERN_COLUMN.matcher(line);

                if (matcher.matches()) {
                    final PgColumn column =
                        table.getColumn(matcher.group(1).trim());
                    column.parseDefinition(matcher.group(2).trim());
                } else {
                    throw new ParserException(
                            ParserException.CANNOT_PARSE_COMMAND + line);
                }
            }
        }
    }

    /**
     * Parses definitions that are present after column definition is
     * closed with ')'.
     *
     * @param table table being parsed
     * @param commands commands being processed
     *
     * @return true if the command was the last command for CREATE TABLE,
     *         otherwise false
     */
    private static String parsePostColumns(
        final PgTable table,
        final String commands) {
        String line = commands;
        final Matcher matcher = PATTERN_INHERITS.matcher(line);

        if (matcher.find()) {
            table.setInherits(matcher.group(1).trim());
            line =
                ParserUtils.removeSubString(
                        line,
                        matcher.start(),
                        matcher.end());
        }

        if (line.contains("WITH OIDS")) {
            table.setWithOIDS(true);
            line = ParserUtils.removeSubString(line, "WITH OIDS");
        } else if (line.contains("WITHOUT OIDS")) {
            table.setWithOIDS(false);
            line = ParserUtils.removeSubString(line, "WITHOUT OIDS");
        }

        return line;
    }

    /**
     * Parses all rows in CREATE TABLE command.
     *
     * @param table table being parsed
     * @param command command without 'CREATE SEQUENCE ... (' string
     *
     * @throws ParserException Thrown if problem occured with parsing of DDL.
     */
    private static void parseRows(final PgTable table, final String command) {
        String line = command;
        boolean postColumns = false;

        try {
            while (line.length() > 0) {
                final int commandEnd = ParserUtils.getCommandEnd(line, 0);
                final String subCommand = line.substring(0, commandEnd).trim();

                if (postColumns) {
                    line = parsePostColumns(table, subCommand);

                    break;
                } else if (line.charAt(commandEnd) == ')') {
                    postColumns = true;
                }

                parseColumnDefs(table, subCommand);
                line =
                    (commandEnd >= line.length()) ? ""
                                                  : line.substring(
                            commandEnd + 1);
            }
        } catch (RuntimeException ex) {
            throw new ParserException(
                    ParserException.CANNOT_PARSE_COMMAND + "CREATE TABLE "
                    + table.getName() + " ( " + command,
                    ex);
        }

        line = line.trim();

        if (line.length() > 0) {
            throw new ParserException(
                    "Cannot parse CREATE TABLE '" + table.getName()
                    + "' - do not know how to parse '" + line + "'");
        }
    }
}
