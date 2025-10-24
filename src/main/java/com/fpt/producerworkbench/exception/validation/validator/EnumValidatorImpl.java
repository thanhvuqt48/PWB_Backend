package com.fpt.producerworkbench.exception.validation.validator;

import com.fpt.producerworkbench.exception.validation.constraint.EnumValidator;
import io.micrometer.common.util.StringUtils;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;

public class EnumValidatorImpl implements ConstraintValidator<EnumValidator, String> {

    private EnumValidator annotation;

    @Override
    public void initialize(EnumValidator constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {
        if(StringUtils.isBlank(value))
            return false;

        Enum<?>[] enumConstants = this.annotation.enumClass().getEnumConstants();
        if(enumConstants == null) return false;
        return Arrays.stream(enumConstants)
                .anyMatch(enumConstant -> enumConstant.name().equals(value));
    }

}
