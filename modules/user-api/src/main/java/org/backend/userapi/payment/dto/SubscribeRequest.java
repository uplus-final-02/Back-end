package org.backend.userapi.payment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import common.enums.PaymentProvider;

@Getter
public class SubscribeRequest {

    @NotNull
    private PaymentProvider paymentProvider;
}