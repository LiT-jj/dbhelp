package com.dbhelp.dto.constraint;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RigidConstraintParseResponse {

    private String dbType;
    private RigidTableRefDto tableRef = new RigidTableRefDto();
    private List<RigidColumnConstraintDto> columns = new ArrayList<>();
}
