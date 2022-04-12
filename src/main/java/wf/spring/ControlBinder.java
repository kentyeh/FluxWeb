package wf.spring;

import java.time.LocalDate;
import java.util.Date;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;
import wf.model.BooleanPropertyEditor;
import wf.model.DatePropertyEditor;
import wf.model.LocalDatePropertyEditor;

/**
 *
 * @author Kent Yeh
 */
@ControllerAdvice
public class ControlBinder {

    /*private MemberManager manager;

    @Autowired
    public void setManager(MemberManager manager) {
        this.manager = manager;
    }*/

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(Date.class, new DatePropertyEditor());
        binder.registerCustomEditor(LocalDate.class, new LocalDatePropertyEditor());
        binder.registerCustomEditor(Boolean.class, new BooleanPropertyEditor());
        //binder.registerCustomEditor(Member.Mono.class, manager);
    }

}
