package dev.serko.safariutils.client;

public final class ChatMessageFilterRegression {
	public static int run() throws Exception {
		var classifier = ChatMessageFilter.class.getDeclaredMethod("doomspiralMessage", String.class);
		classifier.setAccessible(true);
		String line = "The Doomspiral is enraged and can't be caught right now!";
		if (!(boolean) classifier.invoke(null, line)) throw new AssertionError("Enraged feedback missing");
		if ((boolean) classifier.invoke(null, "Player: " + line)) throw new AssertionError("Player message filtered");
		return 2;
	}
	private ChatMessageFilterRegression() { }
}
