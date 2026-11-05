package com.hrms.parity;

import java.util.List;

public interface GoldenExport {
    String name();

    List<String> export() throws Exception;
}
