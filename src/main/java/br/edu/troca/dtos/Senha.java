package br.edu.troca.dtos;

import jakarta.validation.*;
import java.lang.annotation.*;
import java.nio.charset.StandardCharsets;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = Senha.Validator.class)
public @interface Senha {
  String message() default "A senha deve ter entre 8 e 72 bytes UTF-8";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<Senha, String> {
    public boolean isValid(String value, ConstraintValidatorContext c) {
      return value == null
          || (value.getBytes(StandardCharsets.UTF_8).length >= 8
              && value.getBytes(StandardCharsets.UTF_8).length <= 72);
    }
  }
}
