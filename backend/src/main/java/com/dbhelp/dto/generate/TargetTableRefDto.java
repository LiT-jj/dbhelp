package com.dbhelp.dto.generate;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class TargetTableRefDto {
    @NotBlank
    private String catalog;
    private String schema;
    @NotBlank
    private String table;
}
