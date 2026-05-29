package com.verygana2.security.monitoring;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SessionHijackingPattern {
    private String tokenId;
    private String username;
    private boolean userAgentChanged;
    private boolean rapidIPChange;
    private boolean suspicious;
}
