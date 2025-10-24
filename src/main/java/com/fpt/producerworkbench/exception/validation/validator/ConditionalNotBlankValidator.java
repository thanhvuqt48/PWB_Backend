package com.fpt.producerworkbench.exception.validation.validator;

import com.fpt.producerworkbench.exception.validation.constraint.ConditionalNotBlank;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapperImpl;

import java.util.Arrays;
import java.util.Objects;

public class ConditionalNotBlankValidator implements ConstraintValidator<ConditionalNotBlank, Object> {

    private String field;
    private String [] values;
    private String conditionField;

    @Override
    public void initialize(ConditionalNotBlank constraintAnnotation) {
        this.field = constraintAnnotation.field();
        this.values = constraintAnnotation.values();
        this.conditionField = constraintAnnotation.conditionField();
    }

    @Override
    public boolean isValid(Object object, ConstraintValidatorContext constraintValidatorContext) {
        BeanWrapperImpl beanWrapper = new BeanWrapperImpl(object);

        Object conditionValue = beanWrapper.getPropertyValue(conditionField); // messageType

        Object fieldValue = beanWrapper.getPropertyValue(field); // mediaUrl

        boolean conditionMet = fieldValue != null && Arrays.asList(values)
                .contains(Objects.requireNonNull(conditionValue).toString());

        if (conditionMet) {
            if (fieldValue instanceof String string && string.trim().isEmpty()) { // check nếu mediaUrl == null or rỗng
                constraintValidatorContext.disableDefaultConstraintViolation();
                constraintValidatorContext.buildConstraintViolationWithTemplate(
                        String.format("%s cannot be blank when %s is one of %s", field, conditionField, Arrays.toString(values))
                )
                        .addPropertyNode(field)
                        .addConstraintViolation();
                return false;
            }
        }
        return true;
    }
}
