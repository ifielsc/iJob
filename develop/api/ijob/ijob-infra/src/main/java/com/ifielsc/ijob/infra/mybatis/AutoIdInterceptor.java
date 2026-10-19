package com.ifielsc.ijob.infra.mybatis;

import com.ifielsc.ijob.infra.coordination.id.IdGenerator;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

@Intercepts({
    @Signature(
            type = Executor.class,
            method = "update",
            args = {MappedStatement.class, Object.class})
})
@Component
public class AutoIdInterceptor implements Interceptor {
    private final java.util.function.Supplier<IdGenerator> idGenerator;

    @Autowired
    public AutoIdInterceptor(ObjectProvider<IdGenerator> idGeneratorProvider) {
        this((java.util.function.Supplier<IdGenerator>) idGeneratorProvider::getObject);
    }

    public AutoIdInterceptor(IdGenerator idGenerator) {
        this(() -> idGenerator);
    }

    private AutoIdInterceptor(java.util.function.Supplier<IdGenerator> idGenerator) {
        this.idGenerator = idGenerator;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
        if (SqlCommandType.INSERT.equals(mappedStatement.getSqlCommandType())) {
            Object parameter = invocation.getArgs()[1];
            if (parameter != null) {
                processParameter(parameter);
            }
        }
        return invocation.proceed();
    }

    private void processParameter(Object parameter) throws IllegalAccessException {
        if (parameter instanceof Map<?, ?> map) {
            if (map.containsKey("list")) {
                Object list = map.get("list");
                if (list instanceof Collection) {
                    for (Object obj : (Collection<?>) list) {
                        fillId(obj);
                    }
                }
            } else {
                for (Object value : map.values()) {
                    fillId(value);
                }
            }
        } else if (parameter instanceof Collection) {
            for (Object obj : (Collection<?>) parameter) {
                fillId(obj);
            }
        } else {
            fillId(parameter);
        }
    }

    private void fillId(Object obj) throws IllegalAccessException {
        if (obj == null) {
            return;
        }

        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(AutoId.class)) {
                    field.setAccessible(true);
                    if (field.get(obj) == null) {
                        field.set(
                                obj,
                                idGenerator
                                        .get()
                                        .nextId(field.getAnnotation(AutoId.class).value()));
                    }
                    return;
                }
            }
            clazz = clazz.getSuperclass();
        }
    }
}
