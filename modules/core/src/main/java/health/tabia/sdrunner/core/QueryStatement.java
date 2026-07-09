package health.tabia.sdrunner.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handle to a running statement so it can be aborted. Port of the runner's QueryStatement.
 */
public class QueryStatement {
    private static final Logger log = LoggerFactory.getLogger(QueryStatement.class);

    private final String execId;
    private final Connection connection;
    private final PreparedStatement preparedStatement;
    private final AtomicBoolean aborted = new AtomicBoolean(false);

    public QueryStatement(String execId, Connection connection, PreparedStatement preparedStatement) {
        this.execId = execId;
        this.connection = connection;
        this.preparedStatement = preparedStatement;
    }

    public void abort() {
        log.info("Aborting query execId={}", execId);
        try {
            if (!aborted.compareAndSet(false, true)) {
                return;
            }
            if (connection.isClosed()) {
                log.warn("Unable to abort execId={} connection: closed", execId);
                return;
            }
            if (preparedStatement.isClosed()) {
                log.warn("Unable to abort execId={} preparedStatement: closed", execId);
                return;
            }
            preparedStatement.close();
        } catch (Exception e) {
            log.error("Error while aborting execId={} reason={}", execId, e.getMessage());
        }
    }
}
