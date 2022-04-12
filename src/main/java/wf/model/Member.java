package wf.model;

import io.r2dbc.spi.Row;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.annotation.Id;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.domain.Persistable;
import org.springframework.data.r2dbc.mapping.OutboundRow;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.r2dbc.core.Parameter;

/**
 *
 * @author Kent Yeh
 */
@Table("member")
public class Member implements Serializable, Persistable<String> {

    private static final long serialVersionUID = -5382913926664345371L;

    public Member() {
    }

    public Member(String account, String name) {
        this.account = account;
        this.name = name;
    }

    @Id
    @Column
    private String account;

    @Column("username")
    private String name;

    private String passwd;

    private LocalDate birthday;
    
    private String enabled;

    @org.springframework.data.annotation.Transient
    @MappedCollection
    List<String> roles;

    @org.springframework.data.annotation.Transient
    private boolean isnew = true;

    @Override
    public String getId() {
        return account;
    }

    public void setId(String id) {
        this.account = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPasswd() {
        return passwd;
    }

    public void setPasswd(String passwd) {
        this.passwd = passwd;
    }

    public LocalDate getBirthday() {
        return birthday;
    }

    public void setBirthday(LocalDate birthday) {
        this.birthday = birthday;
    }

    public String getEnabled() {
        return enabled;
    }

    public void setEnabled(String enabled) {
        this.enabled = enabled;
    }

    public Member setNew(boolean isnew) {
        this.isnew = isnew;
        return this;
    }

    @Override
    public boolean isNew() {
        return isnew;
    }

    public List<String> getRoles() {
        if (this.roles == null) {
            this.roles = new java.util.ArrayList<>();
        }
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public Member addRole(String rolename) {
        if (roles == null || !roles.contains(rolename)) {
            getRoles().add(rolename);
        }
        return this;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 13 * hash + Objects.hashCode(this.account);
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
        final Member other = (Member) obj;
        return Objects.equals(this.account, other.account);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[").append(account).append("]")
                .append(name);
        if (birthday != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
            sb.append(" born at ").append(birthday.format(formatter));
        }
        if (roles != null && !roles.isEmpty()) {
            boolean first = true;
            for (String role : roles) {
                sb.append(first ? " role(s) is [" : ",").append(role);
                first = false;
            }
            sb.append(']');
        }
        return sb.toString();
    }

    @ReadingConverter
    public static class MemberReadConverter implements Converter<Row, Member> {

        public static Member from(Row row) {
            Member member = new Member();
            member.setId(row.get("account", String.class));
            member.setName(row.get("username", String.class));
            member.setPasswd(row.get("passwd", String.class));
            member.setBirthday(row.get("birthday", LocalDate.class));
            return member;
        }

        @Override
        public Member convert(Row row) {
            return from(row);
        }
    }

    @WritingConverter
    public static class MemberWriteConverter implements Converter<Member, OutboundRow> {

        @Override
        public OutboundRow convert(Member member) {
            OutboundRow row = new OutboundRow();
            row.put("account", Parameter.from(member.getId()));
            row.put("username", Parameter.from(member.getName()));
            row.put("passwd", Parameter.from(member.getPasswd()));
            row.put("birthday", Parameter.from(member.getBirthday()));
            return row;
        }
    }

    public static class Mono extends MonoWrapper<String, Member> {

        private static final long serialVersionUID = -1091892193217072331L;

        public Mono(String key, reactor.core.publisher.Mono<Member> member) {
            super(key, member);
        }
    }
}
