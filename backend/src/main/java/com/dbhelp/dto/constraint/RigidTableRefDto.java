package com.dbhelp.dto.constraint;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RigidTableRefDto {
    private String catalog;
    private String schema;
    private String table;
}
