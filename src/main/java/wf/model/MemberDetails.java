package wf.model;

import java.util.Collections;
import java.util.Objects;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

/**
 *
 * @author Kent Yeh
 */
public class MemberDetails extends User {

    private static final long serialVersionUID = -2682354043609225488L;

    private final Member member;

    public MemberDetails(Member member) {
        super(member.getId(), member.getPasswd(), true, true, true, true, member.roles == null || member.roles.isEmpty()
                ? Collections.<GrantedAuthority>emptyList() : AuthorityUtils.commaSeparatedStringToAuthorityList(String.join(",", member.getRoles())));
        this.member = member;
    }

    @Override
    public boolean isEnabled() {
        return "Y".equals(member.getEnabled());
    }

    @Override
    public String getUsername() {
        return member == null ? super.getUsername() : member.getId();
    }

    @Override
    public String getPassword() {
        return "{noop}" + (member == null ? super.getPassword() : member.getPasswd());
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.member);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final MemberDetails other = (MemberDetails) obj;
        return Objects.equals(this.member, other.member);
    }

    @Override
    public String toString() {
        return member.toString();
    }

}
