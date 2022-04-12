package wf.data;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import wf.model.Member;

/**
 *
 * @author Kent Yeh
 */
@Repository
public interface MemberDao extends ReactiveCrudRepository<Member, String> {

    @Query("SELECT authority FROM authorities WHERE account= :account")
    Flux<String> findMemberRoles(@Param("account") String account);

    @Modifying
    @Query("UPDATE member SET passwd= :newpass WHERE account= :account AND passwd= :oldpass")
    Mono<Integer> changeUserPassword(@Param("account") String account, 
            @Param("newpass") String newpass, @Param("oldpass") String oldpass);
    
    
    @Modifying
    @Query("delete from authorities WHERE account= :account AND authority= :authority")
    Mono<Integer> deleteUserRole(@Param("account") String account,@Param("authority") String authority);
    
    @Modifying
    @Query("INSERT INTO authorities(account,authority) SELECT :account, :authority WHERE NOT EXISTS("
            + "SELECT 1 FROM authorities WHERE account= :account AND authority= :authority)")
    Mono<Integer> insUserRole(@Param("account") String account,@Param("authority") String authority);
    

    @Query("SELECT authority FROM authorities WHERE account= :account")
    Flux<String> findRolesByAccount(@Param("account") String account);
}
