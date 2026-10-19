package com.ifielsc.ijob.api.security;

import com.ifielsc.ijob.contract.auth.AccountPermission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ApiAccess {
    AccountPermission value() default AccountPermission.SUMMARY_READ;

    boolean publicEndpoint() default false;

    boolean selfService() default false;
}
