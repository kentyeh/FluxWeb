package wf.data;

import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import static java.util.stream.Collectors.toList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.Logger;
import wf.model.Member;
import wf.util.Loggers4j2;

/**
 *
 * @author Kent Yeh
 */
@Service("memberManager")
public class MemberManager extends AbstractDaoManager<String, Member> {

    private static final Logger logger = Loggers4j2.getLogger(MemberManager.class);
    private final MemberDao memberDao;

    @Autowired
    public MemberManager(MemberDao memberDao) {
        this.memberDao = memberDao;
    }
    public static final BiFunction<Row, RowMetadata, Member> MAPPING_FUNCTION = (row, rowMetaData) -> {
        Member member = new Member();
        member.setId(row.get("account", String.class));
        member.setName(row.get("username", String.class));
        member.setPasswd(row.get("passwd", String.class));
        member.setBirthday(row.get("birthday", LocalDate.class));
        return member;
    };

    @Override
    public String text2Key(String text) {
        return text;
    }

    @Override
    public Member.Mono findByPrimaryKey(String key) {
        return new Member.Mono(key, findById(key));
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public Mono<Member> findById(String account) {
        return memberDao.findById(account).flatMap(member -> {
            return memberDao.findMemberRoles(account).flatMap(role -> {
                return Mono.just(role);
            }).switchIfEmpty(Flux.just("REJECT"))
                    .collect(toList()).map((List<String> roles) -> {
                        member.setRoles(roles);
                        return member;
                    });
        }).switchIfEmpty(Mono.empty());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Mono<Integer> changePassword(String account, String newpass, String oldpass) {
        return memberDao.changeUserPassword(account, newpass, oldpass);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Mono<Member> saveMember(Member member) {
        Set<String> role2del = new HashSet<>();
        return memberDao.save(member).log(logger).flatMap(m -> {
            return memberDao.findRolesByAccount(member.getId()).flatMap(role -> {
                if (!member.getRoles().stream().anyMatch(role::equals)) {
                    logger.info("準備移除角色： {} ", role);
                    role2del.add(role);
                }
                return Flux.fromIterable(member.getRoles());
            }).switchIfEmpty(Flux.just("ROLE_USER")
            ).flatMap(nr -> {
                logger.info("新增 角色： {}", nr);
                return memberDao.insUserRole(member.getId(), nr);
            }).flatMap(i -> {
                logger.error("共要移除 {} 個角色", role2del.size());
                return Flux.fromIterable(role2del);
            }).reduce("", (f, b) -> ""
            ).flatMapMany(s -> Flux.fromIterable(role2del)
            ).flatMap(dr -> {
                logger.info("移除角色： {}", dr);
                return memberDao.deleteUserRole(member.getId(), dr);
            }).then(findById(member.getId()));
        });
    }
}
