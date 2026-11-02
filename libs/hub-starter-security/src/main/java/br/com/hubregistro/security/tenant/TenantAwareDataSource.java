package br.com.hubregistro.security.tenant;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Aplica o tenant da thread em cada conexão retirada do pool ({@code app.tenant_type} e {@code app.tenant_id},
 * lidos pelas políticas de Row-Level Security) e limpa os dois ao devolver a conexão, para que nenhum
 * tenant vaze para a próxima requisição que reutilizar a mesma conexão.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    private static final String APPLY = "SELECT set_config('app.tenant_type', ?, false), set_config('app.tenant_id', ?, false)";
    private static final String CLEAR = "SELECT set_config('app.tenant_type', '', false), set_config('app.tenant_id', '', false)";

    public TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrap(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrap(super.getConnection(username, password));
    }

    private Connection wrap(Connection connection) throws SQLException {
        TenantContext context = TenantContextHolder.current();
        try (PreparedStatement statement = connection.prepareStatement(APPLY)) {
            statement.setString(1, context.type());
            statement.setString(2, context.tenantAsText());
            statement.execute();
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName()) && !connection.isClosed()) {
                        clear(connection);
                    }
                    if ("unwrap".equals(method.getName()) && args != null && ((Class<?>) args[0]).isInstance(connection)) {
                        return connection;
                    }
                    try {
                        return method.invoke(connection, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    private static void clear(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement(CLEAR)) {
            statement.execute();
        } catch (SQLException ignored) {
            // Conexão inutilizável: o pool a descarta; o próximo uso aplica o tenant de novo de qualquer forma.
        }
    }
}
