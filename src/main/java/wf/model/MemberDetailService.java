package wf.model;

import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import reactor.core.publisher.Mono;
import wf.data.MemberManager;

/**
 *
 * @author Kent Yeh
 */
public class MemberDetailService implements ReactiveUserDetailsService {

    private final MemberManager manager;

    public MemberDetailService(MemberManager manager) {
        this.manager = manager;
    }

    @Override
    public Mono<UserDetails> findByUsername(String account) {
        return manager.findById(account).map(member -> new MemberDetails(member));
    }

}
