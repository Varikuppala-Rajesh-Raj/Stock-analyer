import java.sql.*;
public class H2MigrationProbe {
  public static void main(String[] args) throws Exception {
    String url = "jdbc:h2:mem:flywaycheck;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    String sql = "CREATE TABLE predictions (\n"
      + "  id UUID PRIMARY KEY, symbol VARCHAR(24) NOT NULL, timestamp TIMESTAMP WITH TIME ZONE NOT NULL,\n"
      + "  nifty_price NUMERIC(20,6), horizon_minutes INTEGER NOT NULL,\n"
      + "  up_probability NUMERIC(8,6), down_probability NUMERIC(8,6), sideways_probability NUMERIC(8,6),\n"
      + "  prediction VARCHAR(16), confidence VARCHAR(16), expected_move_points NUMERIC(20,6),\n"
      + "  decision VARCHAR(32) NOT NULL, reason VARCHAR(255), model_version VARCHAR(80) NOT NULL,\n"
      + "  feature_version VARCHAR(80), created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()\n"
      + ");\n"
      + "CREATE INDEX idx_predictions_symbol_timestamp ON predictions(symbol, timestamp DESC);\n"
      + "ALTER TABLE predictions ADD COLUMN predicted_return_percent NUMERIC(12,6), ADD COLUMN predicted_range_low NUMERIC(20,6), ADD COLUMN predicted_range_high NUMERIC(20,6), ADD COLUMN feature_schema_version VARCHAR(100), ADD COLUMN context_timestamp TIMESTAMP WITH TIME ZONE, ADD COLUMN input_hash VARCHAR(128);\n"
      + "ALTER TABLE predictions ADD COLUMN actual_price NUMERIC(20,6), ADD COLUMN actual_return_percent NUMERIC(12,6), ADD COLUMN actual_direction VARCHAR(16), ADD COLUMN outcome_correct BOOLEAN, ADD COLUMN evaluated_at TIMESTAMP WITH TIME ZONE;\n"
      + "CREATE INDEX idx_predictions_outcome_due ON predictions(symbol, evaluated_at, timestamp);\n";
    try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement()) {
      for (String stmt : sql.split(";\\n")) {
        if (!stmt.trim().isEmpty()) {
          System.out.println("Executing: " + stmt.trim());
          s.execute(stmt.trim());
        }
      }
      System.out.println("SUCCESS");
    }
  }
}
