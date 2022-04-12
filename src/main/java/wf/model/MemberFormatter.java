package wf.model;

import java.text.ParseException;
import java.util.Locale;
import org.springframework.format.Formatter;
import reactor.core.publisher.Mono;
import wf.data.MemberManager;

/**
 *
 * @author Kent Yeh
 */
public class MemberFormatter implements Formatter<Member.Mono> {

    private final MemberManager manager;

    public MemberFormatter(MemberManager manager) {
        this.manager = manager;
    }

    @Override
    public String print(Member.Mono mono, Locale locale) {
        String account = mono.key();
        return mono.get().map(member -> member.toString()).switchIfEmpty(Mono.just("查無[" + account + "]帳號")).block();
    }

    @Override
    public Member.Mono parse(String account, Locale locale) throws ParseException {
        return manager.findByPrimaryKey(account);
    }

}
