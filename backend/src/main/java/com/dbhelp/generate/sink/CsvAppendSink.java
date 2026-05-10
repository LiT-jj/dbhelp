package com.dbhelp.generate.sink;

import com.dbhelp.dto.metadata.ColumnEntry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

/**
 * 将一批行追加到 UTF-8 CSV（首写带表头）。
 */
public final class CsvAppendSink {

    private CsvAppendSink() {
    }

    public static void appendBatch(
            Path file,
            List<ColumnEntry> columns,
            List<Map<String, Object>> rows,
            boolean writeHeader) throws IOException {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Files.createDirectories(file.getParent() == null ? Paths.get(".") : file.getParent());
        StringBuilder sb = new StringBuilder();
        if (writeHeader) {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(escapeCsv(columns.get(i).getName()));
            }
            sb.append('\n');
        }
        for (Map<String, Object> row : rows) {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                Object v = row.get(columns.get(i).getName());
                sb.append(escapeCsv(v == null ? "" : String.valueOf(v)));
            }
            sb.append('\n');
        }
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static String escapeCsv(String s) {
        if (s == null) {
            return "";
        }
        boolean need = s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        String t = s.replace("\"", "\"\"");
        return need ? "\"" + t + "\"" : t;
    }
}
