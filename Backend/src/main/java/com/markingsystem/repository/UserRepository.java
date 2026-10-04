package com.markingsystem.repository;

import com.markingsystem.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findFirstByEmailOrderByIdAsc(String email);

    Optional<User> findByStudentNo(String studentNo);

    boolean existsByUsername(String username);

    List<User> findByRoleId(Long roleId);

    @Query("""
            select u from User u
            where (:roleId is null or u.roleId = :roleId)
              and (
                :keyword is null
                or lower(u.username) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(u.nickname, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(u.studentNo, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(u.className, '')) like lower(concat('%', :keyword, '%'))
              )
            """)
    Page<User> searchUsers(@Param("keyword") String keyword, @Param("roleId") Long roleId, Pageable pageable);

    @Query("""
            select count(u) from User u
            where (:roleId is null or u.roleId = :roleId)
              and u.passwordResetRequired = true
              and (
                :keyword is null
                or lower(u.username) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(u.nickname, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(u.studentNo, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(u.className, '')) like lower(concat('%', :keyword, '%'))
              )
            """)
    long countPasswordResetRequiredUsers(@Param("keyword") String keyword, @Param("roleId") Long roleId);

    List<User> findByClassNameIn(List<String> classNames);
}
