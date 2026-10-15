package dev.serko.safariutils;

import dev.serko.safariutils.api.ServiceAccess;
import java.util.Optional;
import java.util.ArrayList;
import java.util.ServiceConfigurationError;
import java.util.concurrent.CompletableFuture;

final class ServiceAccessRegression {
	static int run() {
		var failures = new ArrayList<String>();
		var absent = new ServiceAccess<String>(Optional::empty, (operation, failure) -> failures.add(operation));
		check(!absent.available() && absent.read("read", String::length, -1) == -1, "absent provider uses fallback");
		check(absent.request("request", value -> CompletableFuture.completedFuture(value)).isCompletedExceptionally(), "absent request fails asynchronously");
		var broken = new ServiceAccess<String>(() -> { throw new ServiceConfigurationError("fixture"); },
			(operation, failure) -> failures.add(operation));
		check(!broken.available() && failures.equals(java.util.List.of("load")), "invalid descriptor does not fail class initialization");
		var service = new ServiceAccess<>(() -> Optional.of("value"), (operation, failure) -> failures.add(operation));
		check(service.read("length", String::length, -1) == 5, "normal value unchanged");
		check(service.read("nullable", value -> null, -1) == -1, "null result uses fallback");
		check(service.read("failure", value -> { throw new IllegalStateException(); }, false) == false,
			"runtime failure cannot become false completion evidence");
		service.run("linkage", value -> { throw new NoClassDefFoundError(); });
		check(failures.contains("linkage"), "linkage failure isolated");
		check(service.request("sync-failure", value -> { throw new IllegalStateException(); }).isCompletedExceptionally(), "synchronous request fault delivered as future failure");
		check(service.request("null-future", value -> null).isCompletedExceptionally(), "null future rejected");
		check(service.request("null-result", value -> CompletableFuture.completedFuture(null)).isCompletedExceptionally(), "null asynchronous value rejected");
		var upstream = new CompletableFuture<String>();
		var result = service.request("shared", value -> upstream);
		result.cancel(false);
		check(!upstream.isCancelled(), "consumer cannot cancel shared service work");
		check(service.request("valid", CompletableFuture::completedFuture).join().equals("value"), "later call survives earlier fault");
		var delayed = new CompletableFuture<String>();
		var delayedResult = service.request("delayed", value -> delayed);
		var original = new IllegalStateException("fixture");
		delayed.completeExceptionally(original);
		check(delayedResult.isCompletedExceptionally() && java.util.Collections.frequency(failures, "delayed") == 1,
			"delayed provider failure is recorded once");
		check(delayedResult.handle((value, error) -> error.getCause() == original).join(),
			"original asynchronous failure preserved");
		check(java.util.Collections.frequency(failures, "null-result") == 1,
			"invalid asynchronous null result is diagnosed");
		var cancelledSource = new CompletableFuture<String>();
		var cancellation = service.request("cancelled-source", value -> cancelledSource);
		cancelledSource.cancel(false);
		check(cancellation.isCompletedExceptionally() && !failures.contains("cancelled-source"),
			"normal upstream cancellation is not reported as a service fault");
		check(!failures.contains("shared"), "consumer cancellation is not reported as a service fault");
		return 17;
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
