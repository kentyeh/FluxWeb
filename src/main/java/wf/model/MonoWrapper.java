package wf.model;

import java.io.Serializable;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

/**
 *
 * @author Kent Yeh
 * @param <K>
 * @param <E>
 */
public class MonoWrapper<K, E> implements Serializable, Supplier<Mono<E>> {
    
    private static final long serialVersionUID = -7407688593098091152L;
    
    private final K key;
    private final Mono<E> entity;
    
    public MonoWrapper(K key, Mono<E> entity) {
        this.key = key;
        this.entity = entity;
    }
    
    @Override
    public Mono<E> get() {
        return entity == null ? Mono.empty() : entity.switchIfEmpty(Mono.empty());
    }
    
    public K key() {
        return key;
    }
    
}
