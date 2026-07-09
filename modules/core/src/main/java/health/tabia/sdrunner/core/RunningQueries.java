package health.tabia.sdrunner.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of in-flight queries keyed by execId, enabling abort.
 * Port of the runner's RunningQueriesService.
 */
public class RunningQueries {
    private static final Logger log = LoggerFactory.getLogger(RunningQueries.class);

    private final Map<String, QueryStatement> running = new ConcurrentHashMap<>();

    public void add(String execId, QueryStatement statement) {
        log.debug("Query added execId={}", execId);
        running.put(execId, statement);
    }

    public void finish(String execId) {
        if (running.remove(execId) != null) {
            log.debug("Query finished execId={}", execId);
        }
    }

    public void abort(String execId) {
        running.computeIfPresent(execId, (id, statement) -> {
            statement.abort();
            return null;
        });
    }

    public int size() {
        return running.size();
    }
}
