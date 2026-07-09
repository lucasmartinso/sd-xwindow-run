package health.tabia.sdrunner.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Executes a SQL statement and streams the result via callbacks.
 * Port of the runner's QueryUtils (parameters simplified to positional objects).
 */
public final class QueryRunner {
    private static final Logger log = LoggerFactory.getLogger(QueryRunner.class);

    private QueryRunner() {
    }

    public static void runQuery(boolean modifying,
                                String sql,
                                DataSource dataSource,
                                RunningQueries running,
                                String execId,
                                Integer limit,
                                List<Object> params,
                                BiConsumer<Integer, List<String>> onColumns,
                                BiConsumer<Integer, List<String>> onRow,
                                Runnable onDone,
                                Consumer<Exception> onError) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            if (params != null) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
            }

            running.add(execId, new QueryStatement(execId, connection, ps));

            if (modifying) {
                int affected = ps.executeUpdate();
                if (ps.isClosed()) {
                    throw new Exception("Query aborted by user.");
                }
                onColumns.accept(0, List.of("{ROWS_AFFECTED}"));
                onRow.accept(1, List.of(String.valueOf(affected)));
            } else {
                ResultSet rs = ps.executeQuery();
                if (ps.isClosed()) {
                    throw new Exception("Query aborted by user.");
                }
                int columnCount = rs.getMetaData().getColumnCount();
                List<String> columns = new ArrayList<>(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    columns.add(rs.getMetaData().getColumnLabel(i));
                }
                onColumns.accept(0, columns);

                while (rs.next()) {
                    int rowNumber = rs.getRow();
                    if (limit != null && limit > 0 && limit < rowNumber) {
                        break;
                    }
                    List<String> values = new ArrayList<>(columnCount);
                    for (int i = 1; i <= columnCount; i++) {
                        values.add(rs.getString(i));
                    }
                    onRow.accept(rowNumber, values);
                }
            }
            onDone.run();
        } catch (Exception e) {
            log.debug("Query failed execId={}", execId, e);
            onError.accept(e);
        } finally {
            running.finish(execId);
        }
    }
}
