import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

final class ProducerFiber {
    private static final Logger logger = LoggerFactory.getLogger(ProducerFiber.class);
    private final ScheduledExecutorService scheduler;
    private final int index;
    private final File file;
    private final FileOutputStream out;
    private final long durationMs;
    private final long size;
    private final Long failAtByte; // nullable; guaranteed < size when provided

    // RNG used *only* for content & line breaks (never for chunk size / timing)
    private final Random prng;

    private final long startNs = System.nanoTime();
    private final long durationNs;
    private final double bytesPerNs; // constant emission rate
    private long written = 0L;

    private final CompletableFuture<Void> done = new CompletableFuture<>();
    private ScheduledFuture<?> next;

    private static final byte[] ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 .,;:!?+-*/=_()[]{}<>#'\""
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private static final long MAX_SCHEDULE_DELAY_NS = 50_000_000L; // 50 ms
    private static final int CHUNK_SIZE = 128;                   // fixed for determinism

    // Deterministic line-break state; counts bytes until the next '\n'
    private int lineRemaining;

    ProducerFiber(ScheduledExecutorService scheduler, int index, File file, long size, long durationMs,
                  Long failAtByte, long seed) throws Exception {
        this.scheduler = scheduler;
        this.index = index;
        this.file = file;
        this.out = new FileOutputStream(file);
        this.size = size;
        this.durationMs = durationMs;
        this.failAtByte = failAtByte;
        this.prng = new Random(seed);
        this.durationNs = durationMs * 1_000_000L;
        this.bytesPerNs = (durationNs > 0) ? ((double) size) / (double) durationNs : Double.POSITIVE_INFINITY;

        // First line length deterministic 40..80 inclusive
        this.lineRemaining = 40 + prng.nextInt(41);
    }

    CompletableFuture<Void> start() {
        logger.info("Producer Fiber {} starting: file={}, size={}, durationMs={}, failAtByte={}",
                index, file.getAbsolutePath(), size, durationMs, failAtByte);
        this.next = scheduler.schedule(this::step, 0, TimeUnit.MILLISECONDS);
        return done;
    }

    private void step() {
        try {
            if (done.isDone()) return;

            if (size == 0L) {
                finishAfterRemainingDelay();
                return;
            }

            if (failAtByte != null && written >= failAtByte) {
                failNow();
                return;
            }

            final long elapsedNs = System.nanoTime() - startNs;

            long allowed = (durationNs > 0)
                    ? Math.min(size, (long) Math.floor(bytesPerNs * elapsedNs))
                    : size;

            if (failAtByte != null) {
                allowed = Math.min(allowed, failAtByte);
            }

            long toWrite = allowed - written;

            if (toWrite > 0) {
                writeBytesDeterministic(toWrite);

                if (failAtByte != null && written >= failAtByte) {
                    failNow();
                    return;
                }
                if (written >= size) {
                    finishAfterRemainingDelay();
                    return;
                }

                scheduleNext(timeUntilNextByteNs(elapsedNs));
                return;
            }

            scheduleNext(timeUntilNextByteNs(elapsedNs));

        } catch (Throwable t) {
            completeExceptionally(t);
        }
    }

    private void writeBytesDeterministic(long toWrite) throws Exception {
        while (toWrite > 0) {
            int chunk = (int) Math.min(toWrite, (long) CHUNK_SIZE); // fixed chunk size

            byte[] buf = new byte[chunk];
            for (int i = 0; i < chunk; i++) {
                if (lineRemaining == 0) {
                    buf[i] = (byte) '\n';
                    lineRemaining = 40 + prng.nextInt(41); // deterministic 40..80
                } else {
                    buf[i] = ALPHABET[prng.nextInt(ALPHABET.length)]; // one draw per content byte
                    lineRemaining--;
                }
            }

            out.write(buf);
            out.flush();

            written += chunk;
            toWrite -= chunk;

            if (failAtByte != null && written >= failAtByte) break; // let step() throw immediately after
        }
    }

    private long timeUntilNextByteNs(long elapsedNs) {
        if (durationNs <= 0 || written >= size) return 0L;
        double nextByteIndex = (double) (written + 1);
        long dueAtNs = (long) Math.ceil(nextByteIndex / bytesPerNs);
        long delayNs = dueAtNs - elapsedNs;
        if (delayNs <= 0) return 0L;
        return Math.min(delayNs, MAX_SCHEDULE_DELAY_NS);
    }

    private void scheduleNext(long delayNs) {
        long delayMs = Math.max(0L, delayNs / 1_000_000L);
        this.next = scheduler.schedule(this::step, delayMs, TimeUnit.MILLISECONDS);
    }

    private void finishAfterRemainingDelay() {
        long remainingNs = durationNs - (System.nanoTime() - startNs);
        if (remainingNs <= 0) {
            completeNormally();
        } else {
            scheduleNext(Math.min(remainingNs, MAX_SCHEDULE_DELAY_NS));
        }
    }

    private void failNow() {
        logger.warn("Producer Fiber {}: Failing at requested byte {}", index, failAtByte);
        throw new RuntimeException("Dummy exception: failure requested at byte " + failAtByte);
    }

    private void completeNormally() {
        logger.info("Producer Fiber {}: completeNormally()", index);
        try {
            out.flush();
        } catch (Exception ignore) {
        }
        try {
            out.close();
        } catch (Exception ignore) {
        }
        done.complete(null);
    }

    private void completeExceptionally(Throwable t) {
        logger.info("Producer Fiber {}: completeExceptionally()", index);
        try {
            out.close();
        } catch (Exception ignore) {
        }
        done.completeExceptionally(t);
    }
}
