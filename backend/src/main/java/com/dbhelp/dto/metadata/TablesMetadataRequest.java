package com.dbhelp.dto.metadata;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class TablesMetadataRequest extends ConnectionPayload {
    /** 要枚举表的库列表（对应 {@link CatalogEntry#name}），至少一个 */
    private List<String> catalogs;
}
