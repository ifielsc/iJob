package com.ifielsc.ijob.api.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Explicit command boundary. Only constant field names, never request serialization. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AuditOperation {
    String action();

    String target();

    String fields() default "";

    boolean external() default false;

    boolean accepted() default false;
}
