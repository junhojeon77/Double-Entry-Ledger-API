package com.jun.ledger.domain;

import java.util.UUID;

public record Posting(UUID transferId, UUID accountId, Money amount, Direction direction) {
    
}
