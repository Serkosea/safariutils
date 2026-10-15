package dev.serko.safariutils.concurrent;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Enforces response limits while streaming, before a remote body can fill memory. */
public final class HttpBodies {
	private HttpBodies() { }

	public static HttpResponse.BodyHandler<String> utf8(int maximumBytes) {
		if (maximumBytes < 1) throw new IllegalArgumentException("Response limit must be positive");
		return response -> new Limited<>(maximumBytes,
			HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8));
	}

	public static HttpResponse.BodyHandler<byte[]> bytes(int maximumBytes) {
		if (maximumBytes < 1) throw new IllegalArgumentException("Response limit must be positive");
		return response -> new Limited<>(maximumBytes, HttpResponse.BodySubscribers.ofByteArray());
	}

	/** Bounded streaming for large selectively parsed responses, without buffering the body. */
	public static HttpResponse.BodyHandler<InputStream> stream(int maximumBytes) {
		if (maximumBytes < 1) throw new IllegalArgumentException("Response limit must be positive");
		return response -> new Limited<>(maximumBytes, HttpResponse.BodySubscribers.ofInputStream());
	}

	private static final class Limited<T> implements HttpResponse.BodySubscriber<T> {
		private final HttpResponse.BodySubscriber<T> delegate;
		private final int maximumBytes;
		private long received;
		private Flow.Subscription subscription;
		private boolean finished;

		private Limited(int maximumBytes, HttpResponse.BodySubscriber<T> delegate) {
			this.maximumBytes = maximumBytes;
			this.delegate = delegate;
		}

		@Override
		public CompletionStage<T> getBody() { return delegate.getBody(); }

		@Override
		public void onSubscribe(Flow.Subscription subscription) {
			this.subscription = subscription;
			delegate.onSubscribe(subscription);
		}

		@Override
		public void onNext(List<ByteBuffer> buffers) {
			if (finished) return;
			for (ByteBuffer buffer : buffers) {
				received += buffer.remaining();
				if (received > maximumBytes) {
					subscription.cancel();
					onError(new IOException("Response exceeded byte limit"));
					return;
				}
			}
			delegate.onNext(buffers);
		}

		@Override
		public void onError(Throwable error) {
			if (finished) return;
			finished = true;
			delegate.onError(error);
		}

		@Override
		public void onComplete() {
			if (finished) return;
			finished = true;
			delegate.onComplete();
		}
	}
}
