package wf.data;

import wf.model.MonoWrapper;
import java.beans.PropertyEditorSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 *
 * @author Kent Yeh
 */
@Transactional(readOnly = true)
public abstract class AbstractDaoManager<K, E> extends PropertyEditorSupport {

    @Transactional(propagation = Propagation.SUPPORTS, rollbackFor = Exception.class)
    public abstract MonoWrapper<K,E> findByPrimaryKey(K key);

    /**
     * Convert String to key
     *
     * @param text primary key string.
     * @return primary key
     */
    public abstract K text2Key(String text);

    @Override
    public String getAsText() {
        return getValue() == null ? "" : getValue().toString();
    }

    /**
     *
     * @param text primary key string.
     * @throws IllegalArgumentException
     */
    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        try {
            setValue(StringUtils.hasText(text) ? findByPrimaryKey(text2Key(text)) : null);
        } catch (RuntimeException e) {
            setValue(null);
        }
    }

}
