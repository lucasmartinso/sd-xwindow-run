package health.tabia.sdrunner.core.model;

import java.util.List;

/**
 * Result frame streamed back to the caller. Port of the runner's dto/QueryResult.
 *
 * <ul>
 *   <li>column header: rowNumber = 0, row = column labels</li>
 *   <li>data row: rowNumber = n (1-based), row = string values</li>
 *   <li>end sentinel: rowNumber = Integer.MIN_VALUE, row = null</li>
 *   <li>error: error = true, errorMessage set</li>
 * </ul>
 */
public record QueryResult(Boolean error, String errorMessage, Integer rowNumber, List<String> row) {

    public static final int END_SENTINEL = Integer.MIN_VALUE;

    public QueryResult(Exception exception) {
        this(true, String.valueOf(exception), null, null);
    }

    public QueryResult(Integer rowNumber, List<String> row) {
        this(null, null, rowNumber, row);
    }

    public boolean isError() {
        return Boolean.TRUE.equals(error);
    }

    public boolean isEnd() {
        return rowNumber != null && rowNumber == END_SENTINEL;
    }
}
