import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Synthetic demonstration: no client or employer data. Requires Java 21. */
public class DiagnosticDemo {
    public record Order(int customerId, long cents) {}
    public record Result(Map<Integer, Long> totals, long inspections) {}
    private record Dataset(List<Integer> customers, List<Order> orders) {}

    public static Result baseline(List<Integer> customers, List<Order> orders) {
        Map<Integer, Long> totals = new LinkedHashMap<>();
        long inspections = 0;
        for (int customer : customers) {
            long total = 0;
            for (Order order : orders) {
                inspections++;
                if (order.customerId() == customer) total = Math.addExact(total, order.cents());
            }
            totals.put(customer, total);
        }
        return new Result(Collections.unmodifiableMap(totals), inspections);
    }

    public static Result improved(List<Integer> customers, List<Order> orders) {
        Map<Integer, Long> totals = new LinkedHashMap<>();
        for (int customer : customers) totals.put(customer, 0L);
        for (Order order : orders) {
            // Ignore orders outside the requested customer set, like the baseline.
            if (totals.containsKey(order.customerId())) {
                totals.put(order.customerId(), Math.addExact(totals.get(order.customerId()), order.cents()));
            }
        }
        return new Result(Collections.unmodifiableMap(totals), orders.size());
    }

    private static Dataset dataset() {
        var customers = new ArrayList<Integer>();
        var orders = new ArrayList<Order>();
        for (int i = 0; i < 500; i++) customers.add(i);
        var random = new Random(42);
        for (int i = 0; i < 100_000; i++) {
            orders.add(new Order(random.nextInt(500), 100L + random.nextInt(100_000)));
        }
        return new Dataset(List.copyOf(customers), List.copyOf(orders));
    }

    private static Result run(Dataset data, boolean optimized) {
        return optimized ? improved(data.customers(), data.orders()) : baseline(data.customers(), data.orders());
    }

    private static String json(Result result, long elapsedNanos) {
        StringJoiner totals = new StringJoiner(",", "{", "}");
        result.totals().forEach((customer, cents) -> totals.add("\"" + customer + "\":" + cents));
        return "{\"orderInspections\":" + result.inspections()
            + ",\"elapsedMs\":" + String.format(Locale.ROOT, "%.3f", elapsedNanos / 1_000_000.0)
            + ",\"totalsCents\":" + totals + "}";
    }

    public static void main(String[] args) throws Exception {
        Dataset data = dataset();
        if (args.length > 0 && args[0].equals("serve")) {
            int port = args.length > 1 ? Integer.parseInt(args[1]) : 8765;
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            for (boolean optimized : List.of(false, true)) {
                String path = optimized ? "/after" : "/before";
                server.createContext(path, exchange -> {
                    if (!exchange.getRequestURI().getPath().equals(path)) {
                        exchange.sendResponseHeaders(404, -1);
                        exchange.close();
                        return;
                    }
                    if (!exchange.getRequestMethod().equals("GET")) {
                        exchange.getResponseHeaders().set("Allow", "GET");
                        exchange.sendResponseHeaders(405, -1);
                        exchange.close();
                        return;
                    }
                    long started = System.nanoTime();
                    Result result = run(data, optimized);
                    byte[] body = json(result, System.nanoTime() - started).getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                    exchange.sendResponseHeaders(200, body.length);
                    try (var output = exchange.getResponseBody()) { output.write(body); }
                    exchange.close();
                });
            }
            server.setExecutor(null);
            server.start();
            System.out.println("Demo only, bound to localhost. GET http://127.0.0.1:" + port + "/before or /after");
            return;
        }
        for (int i = 0; i < 5; i++) { run(data, false); run(data, true); }
        long[] beforeTimes = new long[11];
        long[] afterTimes = new long[11];
        Result before = null, after = null;
        for (int i = 0; i < 11; i++) {
            // Alternate order to reduce a systematic first-run advantage.
            for (boolean optimized : (i % 2 == 0 ? List.of(false, true) : List.of(true, false))) {
                long started = System.nanoTime();
                Result result = run(data, optimized);
                long elapsed = System.nanoTime() - started;
                if (optimized) { after = result; afterTimes[i] = elapsed; }
                else { before = result; beforeTimes[i] = elapsed; }
            }
        }
        if (!before.totals().equals(after.totals())) throw new AssertionError("Result mismatch");
        Arrays.sort(beforeTimes);
        Arrays.sort(afterTimes);
        System.out.println("Synthetic local demonstration; not a production benchmark or JMH study.");
        System.out.println("Java: " + System.getProperty("java.version") + "; OS: " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        System.out.println("Dataset: 500 customers, 100000 orders; seed=42; 5 warmups; 11 timed runs per version.");
        System.out.printf(Locale.ROOT, "Before median: %.3f ms; order inspections: %d%n", beforeTimes[5] / 1_000_000.0, before.inspections());
        System.out.printf(Locale.ROOT, "After median: %.3f ms; order inspections: %d%n", afterTimes[5] / 1_000_000.0, after.inspections());
        System.out.println("Results equal: true; total cents: " + after.totals().values().stream().mapToLong(Long::longValue).sum());
        System.out.println("Order inspections reduced 500x. Elapsed-time gains depend on machine, workload and JVM.");
    }
}
