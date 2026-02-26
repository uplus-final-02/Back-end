package user.repository;

import common.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import user.entity.User;
import user.repository.projection.AdminUserRowProjection;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByNickname(String nickname);

    Optional<User> findByNickname(String nickname);

    @Query("SELECT u.id FROM User u WHERE u.userRole = :role")
    List<Long> findIdsByUserRole(@Param("role") UserRole role);

    // [수정] Interface Projection 사용 (Object[] -> UserNicknameInfo)
    @Query("SELECT u.id AS id, u.nickname AS nickname FROM User u WHERE u.id IN :ids")
    List<UserNicknameInfo> findNicknamesByIds(@Param("ids") Collection<Long> ids);

    @Query(
            value = """
            select
                u.id as userId,
                u.nickname as name,
                u.createdAt as createdAt
            from User u
            where
                (:search is null or :search = '')
                or (u.nickname like concat('%', :search, '%'))
                or exists (
                    select 1
                    from AuthAccount a
                    where a.user = u
                      and a.email is not null
                      and a.email like concat('%', :search, '%')
                )
            order by u.createdAt desc
            """,
            countQuery = """
            select count(u)
            from User u
            where
                (:search is null or :search = '')
                or (u.nickname like concat('%', :search, '%'))
                or exists (
                    select 1
                    from AuthAccount a
                    where a.user = u
                      and a.email is not null
                      and a.email like concat('%', :search, '%')
                )
            """
    )
    Page<AdminUserRowProjection> findAdminUserRows(@Param("search") String search, Pageable pageable);
}