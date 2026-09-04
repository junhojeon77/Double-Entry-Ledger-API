package com.jun.ledger.domain;

import java.util.UUID;

public record AccountRef(
    UUID id, String currencyCode) {
}
