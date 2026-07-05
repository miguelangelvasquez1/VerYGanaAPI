package com.verygana2.exceptions.compliance;

import com.verygana2.models.enums.ScreeningList;
import com.verygana2.models.enums.ScreeningStatus;

public class ScreeningHitException extends RuntimeException {

    private final ScreeningList list;
    private final ScreeningStatus status;

    public ScreeningHitException(ScreeningList list, ScreeningStatus status, String queriedName) {
        super("Screening " + status + " on list " + list + " for: " + queriedName);
        this.list = list;
        this.status = status;
    }

    public ScreeningList getList() { return list; }
    public ScreeningStatus getStatus() { return status; }
}