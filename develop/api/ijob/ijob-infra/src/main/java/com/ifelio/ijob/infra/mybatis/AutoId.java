package com.ifelio.ijob.infra.mybatis;

import com.ifelio.ijob.infra.coordination.id.IdNamespace;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoId {
    IdNamespace value();
}
