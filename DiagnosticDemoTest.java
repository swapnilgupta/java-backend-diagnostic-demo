import java.util.List;
import java.util.Map;

public class DiagnosticDemoTest {
    public static void main(String[] args) {
        var customers = List.of(1, 2, 3);
        var orders = List.of(
            new DiagnosticDemo.Order(1, 150),
            new DiagnosticDemo.Order(1, 275),
            new DiagnosticDemo.Order(2, 99),
            new DiagnosticDemo.Order(99, 700));
        var expected = Map.of(1, 425L, 2, 99L, 3, 0L);
        var before = DiagnosticDemo.baseline(customers, orders);
        var after = DiagnosticDemo.improved(customers, orders);
        require(before.totals().equals(expected), "Baseline must match independently calculated totals");
        require(after.totals().equals(expected), "Fix must preserve totals, including customers with no orders");
        require(before.inspections() == 12, "Baseline scans each order for each customer");
        require(after.inspections() == 4, "Improved path visits each order once");
        require(DiagnosticDemo.improved(List.of(), orders).totals().isEmpty(), "Empty customer list");
        require(DiagnosticDemo.improved(customers, List.of()).totals().equals(Map.of(1,0L,2,0L,3,0L)), "Empty orders");
        var large = List.of(new DiagnosticDemo.Order(1, 3_000_000_000L), new DiagnosticDemo.Order(1, 3_000_000_000L));
        require(DiagnosticDemo.improved(List.of(1), large).totals().get(1) == 6_000_000_000L, "Use long integer cents");
        System.out.println("PASS: 7 correctness and work-count checks");
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
