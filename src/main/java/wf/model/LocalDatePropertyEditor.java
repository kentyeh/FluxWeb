package wf.model;

import java.beans.PropertyEditorSupport;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.util.StringUtils;

/**
 *
 * @author Kent Yeh
 */
public class LocalDatePropertyEditor extends PropertyEditorSupport {

    private static final Logger logger = LogManager.getLogger(LocalDatePropertyEditor.class);

    @Override
    public String getAsText() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        return getValue() == null ? "" : ((LocalDate) getValue()).format(formatter);
    }

    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        if (!StringUtils.hasText(text)) {
            setValue(null);
        } else {
            LocalDate dateObj = null;
            for (Map.Entry<String, String> entry : DatePropertyEditor.patterns.entrySet()) {
                if (text.matches(entry.getValue())) {
                    try {
                        dateObj = LocalDate.parse(text, DateTimeFormatter.ofPattern(entry.getKey()));
                    } catch (DateTimeParseException ex) {
                        logger.error(String.format("Failed to convert:%s[%s]", text, entry.getKey()), ex.getMessage(), ex);
                    }
                    break;
                }
            }
            setValue(dateObj);
        }
    }
}
