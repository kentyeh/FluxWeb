package wf.util;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class MissingBeanCondition implements ConfigurationCondition {

    @Override
    public ConfigurationPhase getConfigurationPhase() {
        return ConfigurationPhase.REGISTER_BEAN;
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Class targetBeanType = metadata.getAnnotations()
                .get(ConditionalOnMissingBean.class)
                .getValue("value", Class.class)
                // TODO throw a more informative error
                .orElseThrow(() -> new RuntimeException("無法識別 MissingBeanCondition"));

        try {
            context.getBeanFactory().getBean(targetBeanType);
        } catch (NoSuchBeanDefinitionException e) {
            return true;
        }
        return false;
    }

}
