package wf.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Conditional;

@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Conditional(MissingBeanCondition.class)
public @interface ConditionalOnMissingBean {
    Class<?> value();
}