package com.dbhelp.service.constraint;

import com.dbhelp.dto.constraint.RigidConstraintParseResponse;
import com.dbhelp.dto.metadata.ColumnsMetadataRequest;
import com.dbhelp.service.constraint.mysql.MysqlRigidConstraintResolver;
import com.dbhelp.service.metadata.ConnectionPayloadResolver;
import com.dbhelp.service.metadata.ResolvedConnection;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.SQLException;
import java.util.Locale;

/**
 * 按库品种从系统表推导刚性约束（首期 MySQL）。
 */
@Service
public class RigidConstraintService {

    private final ConnectionPayloadResolver connectionPayloadResolver;

    public RigidConstraintService(ConnectionPayloadResolver connectionPayloadResolver) {
        this.connectionPayloadResolver = connectionPayloadResolver;
    }

    public RigidConstraintParseResponse parse(ColumnsMetadataRequest req) throws SQLException {
        if (!StringUtils.hasText(req.getCatalog())) {
            throw new IllegalArgumentException("catalog 不能为空");
        }
        if (!StringUtils.hasText(req.getTable())) {
            throw new IllegalArgumentException("table 不能为空");
        }
        ResolvedConnection rc = connectionPayloadResolver.resolve(req);
        String db = rc.getDbType().trim().toUpperCase(Locale.ROOT);
        if ("MYSQL".equals(db)) {
            return MysqlRigidConstraintResolver.resolve(rc, req);
        }
        throw new IllegalArgumentException("刚性约束解析当前仅支持 MYSQL，当前为: " + db);
    }
}
