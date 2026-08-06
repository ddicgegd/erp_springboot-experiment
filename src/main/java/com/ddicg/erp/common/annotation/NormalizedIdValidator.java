package com.ddicg.erp.common.annotation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NormalizedIdValidator implements ConstraintValidator<NormalizedId, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        return value.matches("^[a-zA-Z0-9_-]+$");
    }
}
