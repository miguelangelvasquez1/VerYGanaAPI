package com.verygana2.services.interfaces.compliance;

import com.verygana2.services.compliance.ScreeningOutcome;

import java.util.List;

public interface ScreeningPort {

    /**
     * Checks the name and document against all configured restrictive lists.
     * Each implementation decides which lists to include and how to handle the timeout.
     */
    List<ScreeningOutcome> screen(String name, String queriedDocument);
}