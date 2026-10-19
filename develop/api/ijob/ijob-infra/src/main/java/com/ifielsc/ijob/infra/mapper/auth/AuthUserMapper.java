package com.ifielsc.ijob.infra.mapper.auth;

import com.ifielsc.ijob.infra.entity.auth.AuthUser;

import org.apache.ibatis.annotations.Param;

/** Account writes require the auth-accounts guard and caller-side authorization. */
public interface AuthUserMapper {
    AuthUser selectById(@Param("id") Long id);

    AuthUser selectByLoginId(@Param("loginId") String loginId);

    AuthUser selectByUsername(@Param("username") String username);

    long countAll();

    java.util.List<AuthUser> selectPage();

    java.util.List<AuthUser> selectFiltered(
            @Param("namePattern") String namePattern,
            @Param("role") com.ifielsc.ijob.contract.auth.AccountRole role,
            @Param("enabled") Boolean enabled);

    java.util.List<AuthUser> selectApprovers(@Param("namePattern") String namePattern);

    java.util.List<AuthUser> selectApprovalAccounts(
            @Param("loginIds") java.util.List<String> loginIds);

    long countEnabledAdministrators();

    int insert(AuthUser user);

    int deleteByRevision(@Param("id") Long id, @Param("expectedRevision") long expectedRevision);

    int updateProfile(
            @Param("user") AuthUser user, @Param("expectedRevision") long expectedRevision);

    int updateAccess(
            @Param("user") AuthUser user, @Param("expectedRevision") long expectedRevision);

    int updatePassword(
            @Param("user") AuthUser user, @Param("expectedRevision") long expectedRevision);
}
