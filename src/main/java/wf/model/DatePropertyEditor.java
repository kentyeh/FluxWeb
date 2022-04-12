package wf.model;

import java.beans.PropertyEditorSupport;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.util.StringUtils;

/**
 *
 * @author Kent Yeh
 */
public class DatePropertyEditor extends PropertyEditorSupport {

    private static final Logger logger = LogManager.getLogger(DatePropertyEditor.class);
    static final Map<String, String> patterns = new HashMap<>();

    static {
        DatePropertyEditor.patterns.put("yyyyMMdd", "^\\d{8}$");
        DatePropertyEditor.patterns.put("yyyyMMddHHmmss", "^\\d{14}$");
        DatePropertyEditor.patterns.put("yyyy/M/d", "^\\d{4}/\\d{1,2}/\\d{1,2}$");
        DatePropertyEditor.patterns.put("yyyy/M/d H:m", "^\\d{4}/\\d{1,2}/\\d{1,2} \\d{1,2}:\\d{1,2}$");
        DatePropertyEditor.patterns.put("yyyy/M/d H:m:s", "^\\d{4}/\\d{1,2}/\\d{1,2} \\d{1,2}:\\d{1,2}:\\d{1,2}$");
        DatePropertyEditor.patterns.put("yyyy-M-d", "^\\d{4}-\\d{1,2}-\\d{1,2}$");
        DatePropertyEditor.patterns.put("yyyy-M-d H:m", "^\\d{4}-\\d{1,2}-\\d{1,2} \\d{1,2}:\\d{1,2}$");
        DatePropertyEditor.patterns.put("yyyy-M-d H:m:s", "^\\d{4}-\\d{1,2}-\\d{1,2} \\d{1,2}:\\d{1,2}:\\d{1,2}$");
    }

    @Override
    public String getAsText() {
        return getValue() == null ? "" : String.format("%1$tY/%1$tm/%1$td", getValue());
    }

    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        if (!StringUtils.hasText(text)) {
            setValue(null);
        } else if (text instanceof String) {
            Object dateObj = null;
            SimpleDateFormat df = new SimpleDateFormat();
            for (Map.Entry<String, String> entry : DatePropertyEditor.patterns.entrySet()) {
                if (text.matches(entry.getValue())) {
                    df.applyPattern(entry.getKey());
                    try {
                        dateObj = df.parse(text);
                    } catch (Exception e) {
                        logger.error(String.format("Failed to convert:%s[%s]", text, entry.getKey()), e.getMessage(), e);
                    }
                    break;
                }
            }
            setValue(dateObj);
        } else {
            setValue(null);
        }
    }
}
