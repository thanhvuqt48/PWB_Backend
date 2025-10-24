package com.fpt.producerworkbench.exception.validation.constraint;

import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Target(value = {ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ConditionalNotBlank {

    String message() default "{field} cannot be blank when condition is met";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    String field(); // Trường cần kiểm tra (ví dụ: mediaUrl)

    String[] values(); // Các giá trị của trường điều kiện (ví dụ: IMAGE, VIDEO)

    String conditionField(); // Trường điều kiện (ví dụ: messageType)
}
