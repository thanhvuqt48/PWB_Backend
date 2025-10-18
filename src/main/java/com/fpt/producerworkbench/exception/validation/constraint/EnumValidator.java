package com.fpt.producerworkbench.exception.validation.constraint;

import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EnumValidator {

    Class<? extends Enum<?>> enumClass();

    String message() default "Value is not valid for enum.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
