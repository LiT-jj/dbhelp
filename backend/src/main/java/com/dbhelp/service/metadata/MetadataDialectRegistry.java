package com.dbhelp.service.metadata;

import com.dbhelp.service.metadata.dialect.DefaultMetadataDialect;
import com.dbhelp.service.metadata.dialect.MetadataDialect;
import com.dbhelp.service.metadata.dialect.MysqlFamilyMetadataDialect;
import com.dbhelp.service.metadata.dialect.PostgresqlMetadataDialect;
import com.dbhelp.service.metadata.dialect.SqlServerMetadataDialect;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class MetadataDialectRegistry {

    private final MysqlFamilyMetadataDialect mysqlFamily = new MysqlFamilyMetadataDialect();
    private final PostgresqlMetadataDialect postgresql = new PostgresqlMetadataDialect();
    private final SqlServerMetadataDialect sqlServer = new SqlServerMetadataDialect();
    private final DefaultMetadataDialect defaults = new DefaultMetadataDialect();

    public MetadataDialect resolve(String dbType) {
        if (dbType == null) {
            return defaults;
        }
        String t = dbType.trim().toUpperCase(Locale.ROOT);
        switch (t) {
            case "MYSQL":
            case "MARIADB":
            case "GOLDENDB":
                return mysqlFamily;
            case "POSTGRESQL":
                return postgresql;
            case "SQLSERVER":
                return sqlServer;
            default:
                return defaults;
        }
    }
}
