package user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import user.entity.UserPreferredTag;

public interface UserPreferredTagRepository extends JpaRepository<UserPreferredTag, Long> {

    List<UserPreferredTag> findAllByUserId(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM UserPreferredTag upt WHERE upt.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Query("SELECT upt FROM UserPreferredTag upt JOIN FETCH upt.tag WHERE upt.user.id = :userId")
    List<UserPreferredTag> findAllByUserIdWithTag(@Param("userId") Long userId);
}