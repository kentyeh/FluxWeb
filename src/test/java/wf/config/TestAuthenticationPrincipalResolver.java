package wf.config;

import java.lang.annotation.Annotation;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.util.ClassUtils;
import org.springframework.web.reactive.BindingContext;
import reactor.core.publisher.Mono;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import wf.util.Loggers4j2;

/**
 * Copy From
 * https://github.com/spring-projects/spring-security/blob/main/web/src/main/java/org/springframework/security/web/reactive/result/method/annotation/AuthenticationPrincipalArgumentResolver.java
 */
public class TestAuthenticationPrincipalResolver implements HandlerMethodArgumentResolver {

    private static final reactor.util.Logger logger = Loggers4j2.getLogger(TestAuthenticationPrincipalResolver.class);
    private ExpressionParser parser = new SpelExpressionParser();
    private BeanResolver beanResolver;

    public TestAuthenticationPrincipalResolver(BeanFactory beanFactory) {
        this.beanResolver = new BeanFactoryResolver(beanFactory);
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterAnnotation(AuthenticationPrincipal.class) != null;
    }

    @Override
    public Mono<Object> resolveArgument(MethodParameter parameter, BindingContext bindingContext, ServerWebExchange exchange) {
        ReactiveAdapter adapter = ReactiveAdapterRegistry.getSharedInstance().getAdapter(parameter.getParameterType());
        return ReactiveSecurityContextHolder.getContext().map(SecurityContext::getAuthentication)
                .flatMap((authentication) -> {
                    logger.debug("解析 principal 為 {}", authentication.getPrincipal());
                    Mono<Object> principal = Mono
                            .justOrEmpty(resolvePrincipal(parameter, authentication.getPrincipal()));
                    return (adapter != null) ? Mono.just(adapter.fromPublisher(principal)) : principal;
                });
    }

    private Object resolvePrincipal(MethodParameter parameter, Object principal) {
        AuthenticationPrincipal annotation = findMethodAnnotation(AuthenticationPrincipal.class, parameter);
        String expressionToParse = annotation.expression();
        if (StringUtils.hasLength(expressionToParse)) {
            StandardEvaluationContext context = new StandardEvaluationContext();
            context.setRootObject(principal);
            context.setVariable("this", principal);
            context.setBeanResolver(this.beanResolver);
            Expression expression = this.parser.parseExpression(expressionToParse);
            principal = expression.getValue(context);
        }
        if (isInvalidType(parameter, principal)) {
            if (annotation.errorOnInvalidType()) {
                throw new ClassCastException(principal + " is not assignable to " + parameter.getParameterType());
            }
            return null;
        }
        return principal;
    }

    private boolean isInvalidType(MethodParameter parameter, Object principal) {
        if (principal == null) {
            return false;
        }
        Class<?> typeToCheck = parameter.getParameterType();
        boolean isParameterPublisher = Publisher.class.isAssignableFrom(parameter.getParameterType());
        if (isParameterPublisher) {
            ResolvableType resolvableType = ResolvableType.forMethodParameter(parameter);
            Class<?> genericType = resolvableType.resolveGeneric(0);
            if (genericType == null) {
                return false;
            }
            typeToCheck = genericType;
        }
        return !ClassUtils.isAssignable(typeToCheck, principal.getClass());
    }

    /**
     * Obtains the specified {@link Annotation} on the specified
     * {@link MethodParameter}.
     *
     * @param annotationClass the class of the {@link Annotation} to find on the
     * {@link MethodParameter}
     * @param parameter the {@link MethodParameter} to search for an
     * {@link Annotation}
     * @return the {@link Annotation} that was found or null.
     */
    private <T extends Annotation> T findMethodAnnotation(Class<T> annotationClass, MethodParameter parameter) {
        T annotation = parameter.getParameterAnnotation(annotationClass);
        if (annotation != null) {
            return annotation;
        }
        Annotation[] annotationsToSearch = parameter.getParameterAnnotations();
        for (Annotation toSearch : annotationsToSearch) {
            annotation = AnnotationUtils.findAnnotation(toSearch.annotationType(), annotationClass);
            if (annotation != null) {
                return annotation;
            }
        }
        return null;
    }
}
