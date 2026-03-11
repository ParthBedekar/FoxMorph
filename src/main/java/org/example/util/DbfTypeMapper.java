package org.example.util;

import com.linuxense.javadbf.DBFField;

/**
 * Maps Visual FoxPro / dBASE field types to MySQL column type strings.
 */
public final class DbfTypeMapper {

    private DbfTypeMapper() {}

    public static String toMySqlType(DBFField field) {
        return switch ((char) field.getDataType()) {
            case 'I'      -> "INT";
            case 'C'      -> "VARCHAR(" + field.getFieldLength() + ")";
            case 'F', 'N' -> "DECIMAL(" + field.getFieldLength() + "," + field.getDecimalCount() + ")";
            case 'Y'      -> "DECIMAL(19,4)";
            case 'D'      -> "DATE";
            case 'T'      -> "DATETIME";
            case 'L'      -> "BOOLEAN";
            case 'M', 'V' -> "TEXT";
            case 'B'      -> "DOUBLE";
            case 'G', 'P' -> "BLOB";
            default       -> "VARCHAR(255)";
        };
    }

    public static boolean isNumeric(DBFField field) {
        return switch ((char) field.getDataType()) {
            case 'N', 'F', 'Y', 'B' -> true;
            default -> false;
        };
    }

    public static boolean isMemo(DBFField field) {
        char t = (char) field.getDataType();
        return t == 'M' || t == 'G' || t == 'P';
    }

    public static boolean isAutoIncrement(DBFField field) {
        return (char) field.getDataType() == 'I';
    }
}
