package com.dbhelp.generate.types;

/**
 * 与厂商无关的内部类型，用于 Datafaker 取值与写入策略。
 */
public enum SqlCanonicalType {
    BOOLEAN,
    INT32,
    INT64,
    DECIMAL,
    FLOAT,
    DOUBLE,
    STRING,
    DATE,
    TIME,
    DATETIME,
    BYTES,
    UNKNOWN
}
