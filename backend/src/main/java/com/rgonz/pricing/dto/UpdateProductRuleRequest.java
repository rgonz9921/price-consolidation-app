package com.rgonz.pricing.dto;

import com.rgonz.pricing.model.PreferredProvider;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateProductRuleRequest(
        @NotNull(message = "es obligatorio")
        @Size(min = 1, max = 120, message = "debe tener entre 1 y 120 caracteres")
        @Pattern(regexp = "^(\\S(.*\\S)?)?$", message = "no puede empezar ni terminar con espacios")
        String name,

        @NotNull(message = "es obligatorio")
        @DecimalMin(value = "0.00", message = "debe estar entre 0 y 100")
        @DecimalMax(value = "100.00", message = "debe estar entre 0 y 100")
        @Digits(integer = 3, fraction = 2, message = "admite hasta 2 decimales")
        BigDecimal marginPercentage,

        @NotNull(message = "es obligatorio") PreferredProvider preferredProvider,
        @NotNull(message = "es obligatorio") Boolean active) {}
