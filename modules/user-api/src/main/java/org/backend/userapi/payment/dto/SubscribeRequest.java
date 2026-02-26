package org.backend.userapi.payment.dto;

import jakarta.validation.constraints.NotNull;
import common.enums.PaymentMethod;

public record SubscribeRequest(
        @NotNull PaymentProvider provider,
        @NotNull PaymentMethod method
) {
}