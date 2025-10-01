package com.fpt.producerworkbench.exception.validation.validator;

import com.fpt.producerworkbench.exception.validation.constraint.PhoneNumber;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PhoneNumberValidator implements ConstraintValidator<PhoneNumber, String> {
    @Override
    public void initialize(PhoneNumber constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
    }

    @Override
    public boolean isValid(String phoneNumber, ConstraintValidatorContext constraintValidatorContext) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return true;
        }
        String regex = "^(0[1-9])[0-9]{8,9}$";
        return phoneNumber.matches(regex);
    }
}
