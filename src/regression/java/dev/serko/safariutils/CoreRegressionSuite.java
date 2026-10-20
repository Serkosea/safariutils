package dev.serko.safariutils;

import dev.serko.safariutils.concurrent.BoundedRequests;
import dev.serko.safariutils.concurrent.SingleFlight;
import dev.serko.safariutils.concurrent.HttpBodies;
import dev.serko.safariutils.state.LinearTimeline;
import dev.serko.safariutils.state.SafariEpoch;
import dev.serko.safariutils.state.SafariRuntimeState;
import dev.serko.safariutils.client.MinecraftProfiles;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Flow;

/** Headless regressions for shared primitives; no running Minecraft client or network required. */
public final class CoreRegressionSuite {
	private static int checks;

	public static void main(String[] args) throws Exception {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		dev.serko.safariutils.client.ConfigManager.verifyPersistenceModel();
		checks++;
		singleFlight();
		boundedRequests();
		boundedBodies();
		timeline();
		lifecycle();
		profiles();
		checks += SparklingImportRegression.run();
		checks += dev.serko.safariutils.client.CommandSpacingRegression.run();
		checks += PersistenceRegression.run();
		checks += OrderedSnapshotRegression.run();
		checks += DeferredObservationsRegression.run();
		checks += RecoverySnapshotRegression.run();
		checks += ImportBackupRegression.run();
		checks += dev.serko.safariutils.client.SettingsTransferRegression.run();
		checks += dev.serko.safariutils.client.LoggingOptInRegression.run();
		checks += dev.serko.safariutils.client.SavedRunLogRegression.run();
		checks += dev.serko.safariutils.client.SettingsMotionRegression.run();
		checks += dev.serko.safariutils.client.ThemeSelectionRegression.run();
		checks += SettingsFieldsRegression.run();
		checks += GenerationDispatchRegression.run();
		checks += MonotonicCooldownsRegression.run();
		checks += MonotonicTimeRegression.run();
		checks += RecentCacheRegression.run();
		checks += ServiceAccessRegression.run();
		checks += dev.serko.safariutils.client.LookupHistoryRegression.run();
		checks += dev.serko.safariutils.client.ReleaseMetadataRegression.run();
		checks += DeadlineStreamRegression.run();
		checks += dev.serko.safariutils.client.BazaarPricesRegression.run();
		checks += dev.serko.safariutils.client.ChatQueueRegression.run();
		checks += dev.serko.safariutils.client.ChatMessageFilterRegression.run();
		checks += dev.serko.safariutils.client.TicketInvitePolicyRegression.run();
		checks += dev.serko.safariutils.client.TicketGuestPolicyRegression.run();
		checks += dev.serko.safariutils.client.PartySparklingRegression.run();
		checks += dev.serko.safariutils.session.RunHistoryRegression.run();
		checks += dev.serko.safariutils.session.SparklingStatsRegression.run();
		checks += dev.serko.safariutils.client.CapturePairingRegression.run();
		checks += dev.serko.safariutils.client.CaptureOutcomeRegression.run();
		checks += dev.serko.safariutils.client.CaptureLifecycleRegression.run();
		checks += dev.serko.safariutils.client.CritterMarkerGeometryRegression.run();
		checks += dev.serko.safariutils.client.CaptureTransitionsRegression.run();
		checks += dev.serko.safariutils.client.CaptureResolutionsRegression.run();
		checks += dev.serko.safariutils.client.StaticMarkerReplacementRegression.run();
		checks += dev.serko.safariutils.client.ObservedAbsenceRegression.run();
		checks += dev.serko.safariutils.client.ScanPublicationRegression.run();
		checks += dev.serko.safariutils.client.NearbyIndexRegression.run();
		checks += dev.serko.safariutils.client.ScanPairingRegression.run();
		checks += dev.serko.safariutils.client.CritterBodyTypesRegression.run();
		checks += dev.serko.safariutils.client.CritterScanRegression.run();
		checks += dev.serko.safariutils.client.DetectedCountsRegression.run();
		checks += dev.serko.safariutils.client.TickCacheRegression.run();
		checks += dev.serko.safariutils.client.MarkerOwnershipRegression.run();
		checks += dev.serko.safariutils.client.PlayerIdentityStoreRegression.run();
		checks += dev.serko.safariutils.client.StaticEntityCatalogRegression.run();
		checks += dev.serko.safariutils.client.CaptureOwnershipRegression.run();
		if (!dev.serko.safariutils.client.DarknessFilter.suppressesFog(null, null)) {
			throw new AssertionError("Disabled fog effect must be safely excluded before reading client state");
		}
		checks++;
		checks += dev.serko.safariutils.client.CaptureObservationRegression.run();
		checks += dev.serko.safariutils.client.CapturePresentationRegression.run();
		checks += dev.serko.safariutils.client.DiagnosticsRegression.run();
		checks += dev.serko.safariutils.client.DiagnosticReplayRegression.run();
		checks += dev.serko.safariutils.client.CaptureReplayRegression.run();
		checks += dev.serko.safariutils.client.DiagnosticQueueRegression.run();
		checks += dev.serko.safariutils.backend.PlayerCollectionsRegression.run();
		checks += dev.serko.safariutils.backend.SafariPlayerDataRegression.run();
		checks += dev.serko.safariutils.backend.BackendValuesRegression.run();
		// Last: this loopback test closes the process-wide network runtime.
		checks += dev.serko.safariutils.client.NetworkRuntimeRegression.run();
		System.out.println("Core regression checks passed: " + checks);
	}

	private static void singleFlight() {
		var pending = new ConcurrentHashMap<String, CompletableFuture<Integer>>();
		check(SingleFlight.request(pending, "instant", () -> CompletableFuture.completedFuture(7)).join() == 7,
			"immediate success");
		check(pending.isEmpty(), "immediate completion cannot remain in flight");
		check(SingleFlight.request(pending, "failure", () -> { throw new IllegalStateException(); })
			.isCompletedExceptionally(), "synchronous failure delivered asynchronously");
		check(pending.isEmpty(), "failed submission releases ownership");
		var upstream = new CompletableFuture<Integer>();
		var first = SingleFlight.request(pending, "shared", () -> upstream);
		var second = SingleFlight.request(pending, "shared", () -> {
			throw new AssertionError("duplicate work started");
		});
		first.cancel(false);
		check(!upstream.isDone() && !second.isDone(), "consumer cancellation is isolated");
		upstream.complete(11);
		check(second.join() == 11 && pending.isEmpty(), "shared completion releases ownership");
		check(SingleFlight.request(pending, "shared", () -> CompletableFuture.completedFuture(12)).join() == 12,
			"later request starts fresh work");
	}

	private static void boundedRequests() throws Exception {
		try (var runtime = new BoundedRequests("Regression", 1, 1)) {
			CountDownLatch started = new CountDownLatch(1);
			CountDownLatch release = new CountDownLatch(1);
			var running = runtime.submit(() -> {
				started.countDown();
				try { release.await(); }
				catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
				return 1;
			});
			check(started.await(5, TimeUnit.SECONDS), "worker started");
			AtomicInteger executions = new AtomicInteger();
			var queued = runtime.submit(executions::incrementAndGet);
			var rejected = runtime.submit(() -> 3);
			check(rejected.isCompletedExceptionally(), "queue overload is bounded and observable");
			queued.cancel(false);
			var replacement = runtime.submit(executions::incrementAndGet);
			check(!replacement.isDone(), "cancelled queued work immediately frees admission capacity");
			runtime.close();
			running.handle((value, error) -> null).get(5, TimeUnit.SECONDS);
			check(queued.isCompletedExceptionally(), "queued future completes during shutdown");
			check(replacement.isCompletedExceptionally(), "replacement queued future also completes during shutdown");
			check(executions.get() == 0, "shutdown never executes queued work");
			check(runtime.submit(() -> 4).isCompletedExceptionally(), "closed runtime rejects new work");
		}
	}

	private static void timeline() {
		long start = 1_000_000_000L;
		long duration = 550_000_000L;
		var timeline = new LinearTimeline(start, duration);
		check(timeline.progress(start - 1) == 0.0 && timeline.progress(start) == 0.0, "animation starts full size");
		for (int step = 1; step <= 10; step++) {
			check(Math.abs(timeline.progress(start + duration * step / 10) - step / 10.0) < 1e-12,
				"linear frame progress " + step);
		}
		check(timeline.progress(start + duration * 2) == 1.0, "finished animation remains finished");
		var wrapped = new LinearTimeline(Long.MAX_VALUE - 100L, 200L);
		check(wrapped.progress(Long.MIN_VALUE + 49L) == 0.75, "nanoTime rollover keeps elapsed duration");
	}

	private static void boundedBodies() {
		var body = HttpBodies.utf8(4).apply(null);
		AtomicBoolean cancelled = new AtomicBoolean();
		Flow.Subscription subscription = new Flow.Subscription() {
			public void request(long n) { }
			public void cancel() { cancelled.set(true); }
		};
		body.onSubscribe(subscription);
		body.onNext(List.of(ByteBuffer.wrap("ab".getBytes(StandardCharsets.UTF_8))));
		body.onNext(List.of(ByteBuffer.wrap("cd".getBytes(StandardCharsets.UTF_8))));
		body.onComplete();
		check(body.getBody().toCompletableFuture().join().equals("abcd"), "exact response limit accepted");
		check(!cancelled.get(), "valid body does not cancel transport");
		var oversized = HttpBodies.utf8(4).apply(null);
		oversized.onSubscribe(subscription);
		oversized.onNext(List.of(ByteBuffer.wrap(new byte[3])));
		oversized.onNext(List.of(ByteBuffer.wrap(new byte[2])));
		oversized.onComplete();
		check(cancelled.get(), "oversized response cancels transport");
		check(oversized.getBody().toCompletableFuture().isCompletedExceptionally(),
			"response limit applies across chunks and cannot become a success");
		cancelled.set(false);
		var binary = HttpBodies.bytes(4).apply(null);
		binary.onSubscribe(subscription);
		binary.onNext(List.of(ByteBuffer.wrap(new byte[] {0, -1}), ByteBuffer.wrap(new byte[] {2, 3})));
		binary.onComplete();
		check(java.util.Arrays.equals(binary.getBody().toCompletableFuture().join(), new byte[] {0, -1, 2, 3}),
			"binary body preserves bytes across buffers");
		check(!cancelled.get(), "exact binary limit accepted");
		var largeBinary = HttpBodies.bytes(4).apply(null);
		largeBinary.onSubscribe(subscription);
		largeBinary.onNext(List.of(ByteBuffer.wrap(new byte[5])));
		largeBinary.onNext(List.of(ByteBuffer.wrap(new byte[1])));
		largeBinary.onComplete();
		check(cancelled.get() && largeBinary.getBody().toCompletableFuture().isCompletedExceptionally(),
			"oversized binary body cancels and ignores late delivery");
	}

	private static void lifecycle() {
		SafariRuntimeState.connectionJoined();
		SafariEpoch first = SafariRuntimeState.visitStarted("mini-test");
		SafariEpoch active = SafariRuntimeState.runStarted("mini-test");
		check(first.sameVisit(active), "ticket activation preserves visit ownership");
		SafariRuntimeState.visitEnded();
		SafariEpoch next = SafariRuntimeState.visitStarted("mini-test");
		check(!first.sameVisit(next), "same lobby name cannot revive a previous visit");
		check(!active.sameRun(next), "new visit cannot inherit a previous run");
		SafariRuntimeState.visitEnded();
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
		checks++;
	}

	private static void profiles() {
		String uuid = "00000000000000000000000000000001";
		MinecraftProfiles.remember("ExamplePlayer", uuid);
		var cached = MinecraftProfiles.resolve("exampleplayer");
		check(cached.isDone(), "observed profile resolves without background work");
		check(cached.join().name().equals("ExamplePlayer") && cached.join().uuid().equals(uuid),
			"shared resolver retains canonical capitalization and UUID");
		MinecraftProfiles.remember("EXAMPLEPLAYER", uuid);
		check(MinecraftProfiles.resolve("exampleplayer").join().name().equals("EXAMPLEPLAYER"),
			"new authoritative observation replaces stale capitalization");
		check(MinecraftProfiles.resolve("invalid/name").isCompletedExceptionally(),
			"invalid profile name rejected without network");
	}
}
