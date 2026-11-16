# Java Backend Performance Diagnosis

A runnable demonstration of how to reproduce a slow API, identify unnecessary work, fix it and verify that its results remain correct.

**Synthetic portfolio demonstration, prepared with AI assistance.** It contains no employer code or client data and is not evidence of a paid engagement. The account owner should review and understand the code before presenting it as a work sample.

## The issue

A customer-summary API scans all orders separately for every customer. With 500 customers and 100,000 orders, that means **50 million order inspections per request**. A single aggregation pass produces the same totals with **100,000 inspections**.

This demonstrates an application-side repeated-scan bottleneck. It does not measure database queries, network latency, production load or a SQL index.

## Reproduce

Requires JDK 21. No paid services, dependencies, external accounts or customer data.

```sh
mkdir -p build
javac -d build DiagnosticDemo.java DiagnosticDemoTest.java
java -cp build DiagnosticDemoTest
java -cp build DiagnosticDemo
```

For the HTTP demonstration:

```sh
java -cp build DiagnosticDemo serve 8765
```

Open these local endpoints, or request them using curl:

```sh
curl http://127.0.0.1:8765/before
curl http://127.0.0.1:8765/after
```

Both return the same `totalsCents` for 500 customers. The response also reports `orderInspections` and measured `elapsedMs`. Stop the server with Ctrl+C. It binds only to 127.0.0.1. This is a local demonstration, not a production service.

## Diagnosis and fix

1. Reproduce with a fixed random seed and identical inputs.
2. Count actual order inspections instead of assuming the database is slow.
3. Replace nested scans with a single pass over the orders and a map of requested customer totals.
4. Keep money in integer cents using long values and checked addition.
5. Verify known totals, missing customers, unrelated orders, empty datasets and values above 32-bit range.
6. Compare both real HTTP responses and verify method/path handling.

Baseline complexity: O(customers × orders). Improved complexity: expected O(customers + orders), with O(customers) extra memory. Hash-map costs are expected, not strict worst-case guarantees.

## Observed local result

Captured local output:

```text
Synthetic local demonstration; not a production benchmark or JMH study.
Java: 21.0.4; OS: Mac OS X aarch64
Dataset: 500 customers, 100000 orders; seed=42; 5 warmups; 11 timed runs per version.
Before median: 28.006 ms; order inspections: 50000000
After median: 1.876 ms; order inspections: 100000
Results equal: true; total cents: 5014268376
Order inspections reduced 500x. Elapsed-time gains depend on machine, workload and JVM.
```

| Measure | Before | After |
|---|---:|---:|
| Median elapsed time | 28.006 ms | 1.876 ms |
| Order inspections | 50,000,000 | 100,000 |
| Returned customer totals | Identical | Identical |

The count reduction is 500× for this dataset. It is not a 500× latency claim. Timing uses five warmup iterations and eleven alternating-order samples per version; it is a lightweight local demonstration, not a rigorous JMH benchmark. JVM optimization, hardware, load and input distribution affect timings. Avoid promising this improvement for another application.

## Verification

- Seven correctness and work-count checks pass.
- Both running HTTP endpoints return identical totals.
- Exact path matching returns 404 for unknown suffixes.
- POST returns 405 with GET as the allowed method.

## Applying the same method to a real system

Start with one endpoint, a reproducible request and sanitized logs. Establish baseline behavior, profile the request path, identify the bottleneck, agree on the change, then compare correctness and performance under a representative workload. A written diagnosis is a useful deliverable even when a safe fix requires a larger follow-up engagement.
