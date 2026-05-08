package step.testing.metrics;

import step.core.reports.Measure;
import step.core.metrics.CounterMetric;
import step.core.metrics.GaugeMetric;
import step.core.metrics.HistogramMetric;
import step.handlers.javahandler.AbstractKeyword;
import step.handlers.javahandler.Keyword;

import java.util.HashMap;
import java.util.Map;

public class JavaMetricKeyword extends AbstractKeyword {

    @Keyword
    public void MetricKeyword() throws InterruptedException {
        output.startMeasure("My measure");

        // Counter (plain)
        CounterMetric requests = output.newCounter("requests");
        requests.increment();
        requests.increment(5);

        // Counter with labels
        CounterMetric labeledCounter = output.newCounter("requests", Map.of("service", "checkout"));
        labeledCounter.increment(3);

        // Gauge
        GaugeMetric queueDepth = output.newGauge("queue_depth");
        queueDepth.observe(12);
        queueDepth.observe(7);

        // Histogram (plain)
        HistogramMetric responseTimes = output.newHistogram("response_time_ms");
        responseTimes.observe(120);
        responseTimes.observe(340);

        // Histogram with labels
        HistogramMetric labeledHistogram = output.newHistogram("response_time_ms", Map.of("endpoint", "/login"));
        labeledHistogram.observe(200);

        Map<String, Object> measureData = new HashMap<>();
        measureData.put("key", "value");
        Thread.sleep(200);
        output.stopMeasure(Measure.Status.FAILED, measureData);
    }

    @Keyword
    public void LiveReportingMetricKeyword() throws InterruptedException {
        CounterMetric requests = liveReporting.metrics.registerCounter("live_requests");
        CounterMetric checkoutRequests = liveReporting.metrics.registerCounter("live_requests", Map.of("service", "checkout"));
        GaugeMetric queueDepth = liveReporting.metrics.registerGauge("live_queue_depth");
        HistogramMetric responseTimes = liveReporting.metrics.registerHistogram("live_response_time_ms");
        HistogramMetric loginResponseTimes = liveReporting.metrics.registerHistogram("live_response_time_ms", Map.of("endpoint", "/login"));

        // ~20 cycles × 3s = ~60 seconds of visible streaming in Step
        for (int i = 1; i <= 20; i++) {
            liveReporting.measures.startMeasure("live_measure_cycle_" + i);

            requests.increment();
            checkoutRequests.increment(i % 3 == 0 ? 2 : 1);
            queueDepth.observe(5 + (i % 8));
            responseTimes.observe(100 + (i * 15L));
            loginResponseTimes.observe(80 + (i * 12L));

            Thread.sleep(3000);
            liveReporting.measures.stopMeasure(i % 5 == 0 ? Measure.Status.FAILED : Measure.Status.PASSED);
        }
    }
}
