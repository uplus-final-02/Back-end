package org.backend.userapi.history.dto;

import common.enums.HistoryStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SavePointResponse {
    private Long historyId;
    private HistoryStatus status;
    private Integer savedPositionSec;
}
