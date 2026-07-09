package health.tabia.sdrunner.store;

public record QueryHistoryEntry(long id, String profileId, String sql, long executedAt) {
}
