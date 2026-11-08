package net.onelitefeather.coloeus.admin;

import jakarta.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.onelitefeather.coloeus.currency.Currency;
import net.onelitefeather.coloeus.currency.CurrencyService;
import net.onelitefeather.coloeus.persistence.JdbcTransactions;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;
import net.onelitefeather.coloeus.request.RequestValues;

/**
 * The money flow of the staff overview (staff-money-flow spec). Every figure is computed from the journal's postings;
 * nothing is stored twice. Aggregates are computed in SQL and returned as plain decimal strings, so no amount passes
 * through a floating-point number.
 */
@Singleton
public class MoneyFlowService {

    static final Duration WEEK = Duration.ofDays(7);
    static final int DEFAULT_DAYS = 7;
    static final int MAX_DAYS = 366;

    /** The windows of the overview. */
    public enum Window {
        SEVEN_DAYS("7D"),
        ALL("ALL");

        private final String wire;

        Window(String wire) {
            this.wire = wire;
        }

        public String wire() {
            return wire;
        }

        /** Reads the window of a request; absent means {@link #SEVEN_DAYS}. */
        public static Window parse(String value) {
            if (value == null) {
                return SEVEN_DAYS;
            }
            for (Window window : values()) {
                if (window.wire.equals(value)) {
                    return window;
                }
            }
            throw RequestValues.invalid("window must be 7D or ALL");
        }
    }

    /** Client-side totals by currency code. */
    private record Totals(String created, String destroyed, String netClients, String credited, String debited,
                          String netStaff) {
    }

    private final JdbcTransactions transactions;
    private final CurrencyService currencies;
    private final Clock clock;

    public MoneyFlowService(JdbcTransactions transactions, CurrencyService currencies, Clock clock) {
        this.transactions = transactions;
        this.currencies = currencies;
        this.clock = clock;
    }

    /** The overview of every currency and client in a window; read-only, and no account is created. */
    public StaffViews.MoneyFlow flow(Window window) {
        Instant to = clock.instant();
        Instant from = window == Window.SEVEN_DAYS ? to.minus(WEEK) : Instant.EPOCH;
        List<Currency> definitions = currencies.list();
        return transactions.read(connection -> {
            List<StaffViews.ClientFlow> clientFlows = new ArrayList<>();
            List<ClientRow> clients = clients(connection);
            Map<String, Map<String, String[]>> perClient = clientFlowsByCurrency(connection, from, to);
            Map<String, Totals> totals = totals(connection, from, to);
            Map<String, String> circulation = circulation(connection);

            List<StaffViews.CurrencyFlow> currencyFlows = new ArrayList<>();
            List<StaffViews.StaffFlow> staffFlows = new ArrayList<>();
            for (Currency definition : definitions) {
                String code = definition.code();
                Totals total = totals.getOrDefault(code, ZERO_TOTALS);
                currencyFlows.add(new StaffViews.CurrencyFlow(code, total.created(), total.destroyed(),
                        total.netClients(), circulation.getOrDefault(code, "0")));
                staffFlows.add(new StaffViews.StaffFlow(code, total.credited(), total.debited(), total.netStaff()));
            }
            for (ClientRow client : clients) {
                List<StaffViews.ClientCurrencyFlow> rows = new ArrayList<>();
                for (Currency definition : definitions) {
                    String[] cells = perClient.getOrDefault(client.clientId().toString(), Map.of())
                            .get(definition.code());
                    String[] values = cells == null ? new String[] {"0", "0", "0"} : cells;
                    rows.add(new StaffViews.ClientCurrencyFlow(definition.code(), values[0], values[1], values[2]));
                }
                clientFlows.add(new StaffViews.ClientFlow(client.clientId().toString(), client.displayName(), rows));
            }
            return new StaffViews.MoneyFlow(window.wire(), window == Window.SEVEN_DAYS ? from : null, to,
                    currencyFlows, clientFlows, staffFlows);
        });
    }

    /**
     * The created and destroyed money of the clients of one currency per UTC day, oldest first, zero-filled over the
     * last {@code days} days including today.
     */
    public StaffViews.MoneySeries series(String currencyCode, Integer days) {
        int size = days == null ? DEFAULT_DAYS : days;
        if (size < 1 || size > MAX_DAYS) {
            throw RequestValues.invalid("days must be between 1 and " + MAX_DAYS);
        }
        if (currencyCode == null || currencyCode.isBlank()) {
            throw RequestValues.invalid("currency is required");
        }
        boolean known = currencies.list().stream().anyMatch(currency -> currency.code().equals(currencyCode));
        if (!known) {
            throw ProblemException.of(ProblemCode.CURRENCY_NOT_FOUND, "currency " + currencyCode + " does not exist");
        }
        LocalDate today = clock.instant().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate first = today.minusDays(size - 1L);
        Instant start = first.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Map<String, String[]> byDay = transactions.read(connection -> seriesRows(connection, currencyCode, start, end));
        List<StaffViews.DayFlow> result = new ArrayList<>();
        for (LocalDate day = first; !day.isAfter(today); day = day.plusDays(1)) {
            String[] values = byDay.getOrDefault(day.toString(), new String[] {"0", "0"});
            result.add(new StaffViews.DayFlow(day.toString(), values[0], values[1]));
        }
        return new StaffViews.MoneySeries(currencyCode, result);
    }

    private static final Totals ZERO_TOTALS = new Totals("0", "0", "0", "0", "0", "0");

    private record ClientRow(UUID clientId, String displayName) {
    }

    private static List<ClientRow> clients(Connection connection) throws SQLException {
        List<ClientRow> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT client_id, display_name FROM client ORDER BY display_name, client_id");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                result.add(new ClientRow(rows.getObject("client_id", UUID.class), rows.getString("display_name")));
            }
        }
        return result;
    }

    /** Per client id and currency: created, destroyed and net, as text. */
    private static Map<String, Map<String, String[]>> clientFlowsByCurrency(Connection connection, Instant from,
                                                                            Instant to) throws SQLException {
        String sql = "SELECT cl.client_id, cu.code, "
                + "trim_scale(-COALESCE(SUM(CASE WHEN a.kind = 'SOURCE' THEN p.amount END), 0))::text AS created, "
                + "trim_scale(COALESCE(SUM(CASE WHEN a.kind = 'SINK' THEN p.amount END), 0))::text AS destroyed, "
                + "trim_scale(-COALESCE(SUM(CASE WHEN a.kind = 'SOURCE' THEN p.amount END), 0) "
                + "- COALESCE(SUM(CASE WHEN a.kind = 'SINK' THEN p.amount END), 0))::text AS net "
                + "FROM client cl CROSS JOIN currency cu "
                + "LEFT JOIN account a ON a.client_id = cl.client_id "
                + "LEFT JOIN posting p ON p.account_id = a.id AND p.currency = cu.code "
                + "AND p.created_at >= ? AND p.created_at <= ? "
                + "GROUP BY cl.client_id, cu.code";
        Map<String, Map<String, String[]>> result = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(from));
            statement.setTimestamp(2, Timestamp.from(to));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.computeIfAbsent(rows.getObject("client_id", UUID.class).toString(), key -> new HashMap<>())
                            .put(rows.getString("code"), new String[] {
                                    rows.getString("created"), rows.getString("destroyed"), rows.getString("net")});
                }
            }
        }
        return result;
    }

    /** Totals of all clients and of the staff bucket per currency code. */
    private static Map<String, Totals> totals(Connection connection, Instant from, Instant to) throws SQLException {
        String sql = "SELECT cu.code, "
                + "trim_scale(-COALESCE(SUM(CASE WHEN a.kind = 'SOURCE' THEN p.amount END), 0))::text AS created, "
                + "trim_scale(COALESCE(SUM(CASE WHEN a.kind = 'SINK' THEN p.amount END), 0))::text AS destroyed, "
                + "trim_scale(-COALESCE(SUM(CASE WHEN a.kind = 'SOURCE' THEN p.amount END), 0) "
                + "- COALESCE(SUM(CASE WHEN a.kind = 'SINK' THEN p.amount END), 0))::text AS net_clients, "
                + "trim_scale(-COALESCE(SUM(CASE WHEN a.kind = 'ADMIN_SOURCE' THEN p.amount END), 0))::text AS credited, "
                + "trim_scale(COALESCE(SUM(CASE WHEN a.kind = 'ADMIN_SINK' THEN p.amount END), 0))::text AS debited, "
                + "trim_scale(-COALESCE(SUM(CASE WHEN a.kind = 'ADMIN_SOURCE' THEN p.amount END), 0) "
                + "- COALESCE(SUM(CASE WHEN a.kind = 'ADMIN_SINK' THEN p.amount END), 0))::text AS net_staff "
                + "FROM currency cu LEFT JOIN posting p ON p.currency = cu.code "
                + "AND p.created_at >= ? AND p.created_at <= ? "
                + "LEFT JOIN account a ON a.id = p.account_id GROUP BY cu.code";
        Map<String, Totals> result = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(from));
            statement.setTimestamp(2, Timestamp.from(to));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.put(rows.getString("code"), new Totals(
                            rows.getString("created"),
                            rows.getString("destroyed"),
                            rows.getString("net_clients"),
                            rows.getString("credited"),
                            rows.getString("debited"),
                            rows.getString("net_staff")));
                }
            }
        }
        return result;
    }

    /** The current sum of the PLAYER balances per currency code; independent of any window. */
    private static Map<String, String> circulation(Connection connection) throws SQLException {
        String sql = "SELECT cu.code, trim_scale(COALESCE(SUM(CASE WHEN a.kind = 'PLAYER' THEN b.amount END), 0))::text "
                + "AS circulation FROM currency cu LEFT JOIN balance b ON b.currency = cu.code "
                + "LEFT JOIN account a ON a.id = b.account_id GROUP BY cu.code";
        Map<String, String> result = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                result.put(rows.getString("code"), rows.getString("circulation"));
            }
        }
        return result;
    }

    /** Per UTC day (as {@code YYYY-MM-DD}): the clients' created and destroyed money, as text. */
    private static Map<String, String[]> seriesRows(Connection connection, String currency, Instant start, Instant end)
            throws SQLException {
        String sql = "SELECT to_char((p.created_at AT TIME ZONE 'UTC')::date, 'YYYY-MM-DD') AS day, "
                + "trim_scale(-COALESCE(SUM(CASE WHEN a.kind = 'SOURCE' THEN p.amount END), 0))::text AS created, "
                + "trim_scale(COALESCE(SUM(CASE WHEN a.kind = 'SINK' THEN p.amount END), 0))::text AS destroyed "
                + "FROM posting p JOIN account a ON a.id = p.account_id "
                + "WHERE p.currency = ? AND a.kind IN ('SOURCE', 'SINK') AND p.created_at >= ? AND p.created_at < ? "
                + "GROUP BY 1";
        Map<String, String[]> result = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, currency);
            statement.setTimestamp(2, Timestamp.from(start));
            statement.setTimestamp(3, Timestamp.from(end));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.put(rows.getString("day"), new String[] {rows.getString("created"),
                            rows.getString("destroyed")});
                }
            }
        }
        return result;
    }

    /** The wire value of a window, for the answer. */
    static String wire(Window window) {
        return window.wire();
    }
}
