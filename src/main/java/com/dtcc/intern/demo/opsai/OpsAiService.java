package com.dtcc.intern.demo.opsai;

import com.dtcc.intern.demo.dto.OpsAiResponse;
import com.dtcc.intern.demo.entity.Incident;

public interface OpsAiService {

    OpsAiResponse assist(Incident incident, OpsAiAction action);
}
