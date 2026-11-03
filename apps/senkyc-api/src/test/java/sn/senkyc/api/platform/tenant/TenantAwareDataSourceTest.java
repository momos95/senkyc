package sn.senkyc.api.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TenantAwareDataSourceTest {

    private final DataSource target = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement statement = mock(PreparedStatement.class);
    private final TenantAwareDataSource dataSource = new TenantAwareDataSource(target);

    @BeforeEach
    void setUp() throws SQLException {
        when(target.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(TenantAwareDataSource.SET_TENANT_SQL)).thenReturn(statement);
    }

    @Test
    void setsTheCurrentTenantOnEveryConnection() throws SQLException {
        UUID tenant = UUID.randomUUID();

        Connection result = TenantContext.callAs(tenant, () -> {
            try {
                return dataSource.getConnection();
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });

        assertThat(result).isSameAs(connection);
        verify(statement).setString(1, tenant.toString());
        verify(statement).execute();
    }

    @Test
    void setsAnEmptyValueWhenNoTenantSoThatNothingLeaksFromAPreviousUser() throws SQLException {
        dataSource.getConnection();

        verify(statement).setString(1, "");
    }

    @Test
    void closesTheConnectionAndPropagatesWhenBindingFails() throws SQLException {
        when(connection.prepareStatement(anyString())).thenThrow(new SQLException("boom"));

        assertThatThrownBy(() -> dataSource.getConnection()).isInstanceOf(SQLException.class);

        verify(connection).close();
    }
}
