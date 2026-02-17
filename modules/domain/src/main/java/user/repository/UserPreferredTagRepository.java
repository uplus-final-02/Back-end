package user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import user.entity.UserPreferredTag;

public interface UserPreferredTagRepository extends JpaRepository<UserPreferredTag, Long> {

    List<UserPreferredTag> findAllByUserId(Long userId);
    
    @Modifying
    @Transactional
    void deleteByUserId(Long userId);
}
