package com.github.guocay.hj212.core.validator.clazz;

import com.github.guocay.hj212.model.verify.ProtocolMap;
import jakarta.validation.ConstraintValidator;

import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * @author aCay
 */
public abstract class ProtocolMapFieldValidator<A extends Annotation,AF extends Annotation>
        extends FieldValidator<A, ProtocolMap<String,?>,AF,String> {

    private Predicate<String> predicate;
    private ConstraintValidator<AF, ? super String> fieldValidator;

    public ProtocolMapFieldValidator(ConstraintValidator<AF, ? super String> constraintValidator) {
        super(constraintValidator);
        this.fieldValidator = constraintValidator;
    }

    @Override
    public void initialize(A a) {
        super.initialize(a);
        annotation = getAnnotation(a);
        predicate = null;
        if(isFieldRegex(a)){
            predicate = Pattern.compile(field).asPredicate();
        }
    }

    public abstract boolean isFieldRegex(A a);

    @Override
    public boolean isValid(ProtocolMap<String, ?> value, jakarta.validation.ConstraintValidatorContext context) {
        if (predicate == null) return super.isValid(value, context);
        boolean valid = true;
        for (String key : value.keySet()) {
            if (predicate.test(key) && !fieldValidator.isValid((String) value.get(key), context)) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(getFieldMessage(annotation))
                    .addPropertyNode(key).addConstraintViolation();
                valid = false;
            }
        }
        return valid;
    }

    private AF annotation;

    @Override
    public String getFieldValue(ProtocolMap<String,?> value, String field) {
        if(predicate != null){
            Optional<String> optional = value.keySet()
                    .stream()
                    .filter(key -> predicate.test(key))
                    .findFirst();
            if(optional.isPresent()){
                return (String) value.get(optional.get());
            }
        }
        if(!value.containsKey(field)){
            return null;
        }
        return (String) value.get(field);
    }

}
