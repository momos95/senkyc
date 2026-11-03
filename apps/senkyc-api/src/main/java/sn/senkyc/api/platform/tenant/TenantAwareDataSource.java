package sn.senkyc.api.platform.tenant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Positionne le paramètre de session {@code app.tenant_id} à CHAQUE emprunt de connexion, à partir
 * du {@link TenantContext} (ou chaîne vide s'il est absent). Les politiques RLS de PostgreSQL s'appuient
 * sur ce paramètre. Comme la valeur est réécrite à chaque emprunt, aucune valeur d'un utilisateur
 * précédent ne peut subsister dans une connexion du pool.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    static final String SET_TENANT_SQL = "select set_config('app.tenant_id', ?, false)";

    public TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return bind(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return bind(super.getConnection(username, password));
    }

    private static Connection bind(Connection connection) throws SQLException {
        String tenant = TenantContext.current().map(UUID::toString).orElse("");
        try (PreparedStatement statement = connection.prepareStatement(SET_TENANT_SQL)) {
            statement.setString(1, tenant);
            statement.execute();
        } catch (SQLException | RuntimeException e) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // la connexion d'origine est déjà en échec ; on propage l'erreur initiale
            }
            throw e;
        }
        return connection;
    }
}
