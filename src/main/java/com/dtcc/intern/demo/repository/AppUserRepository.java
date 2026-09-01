package com.dtcc.intern.demo.repository;

import com.dtcc.intern.demo.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            select u from AppUser u
            where upper(u.role.roleName) = 'SUPPORT'
              and u.team.teamId = :teamId
            """)
    List<AppUser> findSupportEngineersByTeam(@Param("teamId") Long teamId);
}
