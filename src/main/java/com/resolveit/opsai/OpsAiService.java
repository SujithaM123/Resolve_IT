package com.resolveit.opsai;

import com.resolveit.dto.OpsAiResponse;
import com.resolveit.entity.Incident;

public interface OpsAiService {

    OpsAiResponse assist(Incident incident, OpsAiAction action);
}
