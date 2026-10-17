package com.ifelio.ijob.core.resource;

import org.springframework.jdbc.datasource.AbstractDataSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import javax.sql.DataSource;

/** Acquires the current resource generation at getConnection, not when the facade is obtained. */
final class LeasedDataSource extends AbstractDataSource {
    private final Supplier<ResourceClientLease<DataSource>> leases;

    LeasedDataSource(Supplier<ResourceClientLease<DataSource>> leases) {
        this.leases = leases;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return open(null, null, false);
    }

    @Override
    public Connection getConnection(String user, String password) throws SQLException {
        return open(user, password, true);
    }

    private Connection open(String user, String password, boolean explicitCredentials)
            throws SQLException {
        ResourceClientLease<DataSource> lease = leases.get();
        try {
            Connection connection =
                    explicitCredentials
                            ? lease.getClient().getConnection(user, password)
                            : lease.getClient().getConnection();
            AtomicBoolean closed = new AtomicBoolean();
            return (Connection)
                    Proxy.newProxyInstance(
                            Connection.class.getClassLoader(),
                            new Class<?>[] {Connection.class},
                            (proxy, method, args) -> {
                                String name = method.getName();
                                if (name.equals("close") || name.equals("abort")) {
                                    if (closed.compareAndSet(false, true)) {
                                        try {
                                            method.invoke(connection, args);
                                        } catch (InvocationTargetException exception) {
                                            throw exception.getCause();
                                        } finally {
                                            lease.close();
                                        }
                                    }
                                    return null;
                                }
                                if (name.equals("isClosed") && closed.get()) return true;
                                if (name.equals("unwrap") && ((Class<?>) args[0]).isInstance(proxy))
                                    return proxy;
                                if (name.equals("isWrapperFor")
                                        && ((Class<?>) args[0]).isInstance(proxy)) return true;
                                try {
                                    return method.invoke(connection, args);
                                } catch (InvocationTargetException exception) {
                                    throw exception.getCause();
                                }
                            });
        } catch (SQLException | RuntimeException | Error failure) {
            lease.close();
            throw failure;
        }
    }
}
