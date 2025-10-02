import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.handlers.javahandler.AbstractKeyword;
import step.handlers.javahandler.Input;
import step.handlers.javahandler.Keyword;
import step.reporting.LiveReporting;
import step.streaming.client.upload.StreamingUpload;
import step.streaming.common.StreamingResourceMetadata;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class StreamingUploadsTestKeyword extends AbstractKeyword {
    private static final Logger logger = LoggerFactory.getLogger(StreamingUploadsTestKeyword.class);

    public void junitSetLiveReporting(LiveReporting liveReporting) {
        super.liveReporting = liveReporting;
    }

    // ---- shared, tiny scheduler (create once, shut down at the very end)
    private static ScheduledThreadPoolExecutor newScheduler(int threads) {
        ThreadFactory tf = new ThreadFactory() {
            final ThreadFactory base = Executors.defaultThreadFactory();
            final AtomicInteger seq = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = base.newThread(r);
                t.setName("producer-scheduler-with-" + threads + "-threads-" + seq.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };
        ScheduledThreadPoolExecutor sch = new ScheduledThreadPoolExecutor(threads, tf);
        sch.setRemoveOnCancelPolicy(true);  // keep the queue clean on cancellations
        return sch;
    }

    @Keyword
    public void StreamingUploadsTest(
            @Input(name = "attachmentsCount", defaultValue = "2") int attachmentsCount,
            @Input(name = "attachmentSizeMin", defaultValue = "100") int attachmentSizeMin,
            @Input(name = "attachmentSizeMax", defaultValue = "100000") int attachmentSizeMax,
            @Input(name = "productionTimeSecondsMin", defaultValue = "10") int productionTimeSecondsMin,
            @Input(name = "productionTimeSecondsMax", defaultValue = "30") int productionTimeSecondsMax,
            @Input(name = "sleepBetweenSecondsMin", defaultValue = "1") int sleepBetweenSecondsMin,
            @Input(name = "sleepBetweenSecondsMax", defaultValue = "3") int sleepBetweenSecondsMax,
            @Input(name = "failingIndexes", defaultValue = "-1;-1") List<Integer> failingIndexes,
            @Input(name = "forgetToCompleteIndexes", defaultValue = "-1;-1") List<Integer> forgetToCompleteIndexes,
            @Input(name = "mimeType", defaultValue = "text/plain") String mimeType,
            @Input(name = "randomSeedNumber", defaultValue = "31337") String randomSeedNumber,
            @Input(name = "producerThreads", defaultValue = "2") int producerThreads)
            throws Exception {

        List<Path> files = new ArrayList<>();
        List<StreamingUpload> uploads = new ArrayList<>();
        List<CompletableFuture<Void>> producerFutures = new ArrayList<>();
        List<CompletableFuture<Void>> doneFutures = new ArrayList<>();

        ScheduledThreadPoolExecutor scheduler = null;
        try {
            if (attachmentSizeMin < 0 || attachmentSizeMax < attachmentSizeMin) {
                throw new IllegalArgumentException("illegal attachment size params (must be >=0)");
            }
            if (sleepBetweenSecondsMin < 0 || sleepBetweenSecondsMax < sleepBetweenSecondsMin) {
                throw new IllegalArgumentException("illegal sleep params (must be >=0)");
            }
            if (productionTimeSecondsMin < 1 || productionTimeSecondsMax < productionTimeSecondsMin) {
                throw new IllegalArgumentException("illegal production time params (must be > 0)");
            }

            int nThreads = Math.min(attachmentsCount, producerThreads);
            scheduler = newScheduler(nThreads);
            logger.info("Using {} native threads for scheduling {} producer fibers", nThreads, attachmentsCount);

            SplittableRandom random; // because it has nextInt(min,max)
            if (randomSeedNumber == null || randomSeedNumber.isBlank()) {
                random = new SplittableRandom();
            } else {
                random = new SplittableRandom(Long.parseLong(randomSeedNumber));
            }

            for (int attachmentIndex = 0; attachmentIndex < attachmentsCount; attachmentIndex++) {
                final int index = attachmentIndex;
                if (index != 0) {
                    long sleep = nextInt(random, sleepBetweenSecondsMin * 1000, sleepBetweenSecondsMax * 1000);
                    Thread.sleep(sleep);
                }
                long fileSize = nextInt(random, attachmentSizeMin, attachmentSizeMax);
                long durationMs = nextInt(random, productionTimeSecondsMin, productionTimeSecondsMax) * 1000L;
                Long failAtByte = failingIndexes.contains(index) ? random.nextLong(0, fileSize / 2) : null;

                Path file = Files.createTempFile("stream-" + index + "-", ".txt"); // FIXME: .bin
                files.add(file);

                StreamingUpload upload;

                try {
                    if (mimeType.equals("text/plain")) {
                        // use simple high-level API
                        upload = liveReporting.fileUploads.startTextFileUpload(file.toFile());
                    } else {
                        // user lower-level API for explicitly setting mimeType
                        var metadata = new StreamingResourceMetadata(file.toFile().getName(), mimeType, false);
                        var session = liveReporting.fileUploads.getProvider().startLiveBinaryFileUpload(file.toFile(), metadata);
                        upload = new StreamingUpload(session);
                    }
                    uploads.add(upload);
                } catch (Exception e) {
                    logger.error("Error starting upload", e);
                    continue;
                }

                ProducerFiber producer = new ProducerFiber(scheduler,
                        index, file.toFile(), fileSize, durationMs,
                        failAtByte, random.nextLong());

                doneFutures.add(new CompletableFuture<>());
                CompletableFuture<Void> future = producer.start();
                producerFutures.add(future);
                future.whenComplete((r, ex) -> {
                    if (ex == null) {
                        CompletableFuture.runAsync(() -> {
                            if (forgetToCompleteIndexes.contains(index)) {
                                logger.warn("Forgetting to complete upload {} as requested", index);
                            } else {
                                logger.info("Completing upload {} normally", index);
                                try {
                                    var result = uploads.get(index).complete();
                                    logger.info("upload {} completed: {}", index, result);
                                } catch (Exception e) {
                                    doneFutures.get(index).completeExceptionally(e);
                                }
                            }
                            doneFutures.get(index).complete(null);
                        });
                    } else {
                        logger.warn("Upload {} failed: {}", index, ex.getMessage());
                        doneFutures.get(index).completeExceptionally(ex);
                    }
                });
            }

            logger.info("{} producers started, awaiting results", producerFutures.size());
            try {
                CompletableFuture.allOf(producerFutures.toArray(new CompletableFuture[0])).join();
            } catch (Exception ignored) {
            }
            logger.info("all producers completed; completing uploads");
            for (int i = 0; i < uploads.size(); ++i) {
                try {
                    doneFutures.get(i).join();
                } catch (Exception throwable) {
                    logger.warn("upload {} failed: {}", i, throwable.getMessage());
                    uploads.get(i).cancel(throwable);
                    if (output != null) {
                        String msg = null;
                        Throwable leaf = throwable;
                        while (leaf.getCause() != null) {
                            leaf = throwable.getCause();
                        }
                        msg = leaf.getMessage();
                        if (msg == null) {
                            msg = throwable.toString();
                        }
                        output.add("producer-" + i + "-exception", msg);
                    }
                }
            }
            logger.info("all uploads completed");
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            for (Path file : files) {
                Files.deleteIfExists(file);
            }
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
        }
        logger.info("Keyword execution finished");
    }


    private int nextInt(SplittableRandom random, int min, int max) {
        if (min == max) return min;
        return random.nextInt(min, max);
    }

}
