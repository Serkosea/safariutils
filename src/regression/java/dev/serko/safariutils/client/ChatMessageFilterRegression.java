package dev.serko.safariutils.client;

public final class ChatMessageFilterRegression {
	public static int run() throws Exception {
		var classifier = ChatMessageFilter.class.getDeclaredMethod("doomspiralMessage", String.class);
		classifier.setAccessible(true);
		String line = "The Doomspiral is enraged and can't be caught right now!";
		if (!(boolean) classifier.invoke(null, line)) throw new AssertionError("Enraged feedback missing");
		if ((boolean) classifier.invoke(null, "Player: " + line)) throw new AssertionError("Player message filtered");
		classifier = ChatMessageFilter.class.getDeclaredMethod("wumpaMessage", String.class);
		classifier.setAccessible(true);
		line = "The Wumpa is enraged and can't be caught right now!";
		if (!(boolean) classifier.invoke(null, line)) throw new AssertionError("Wumpa feedback missing");
		if ((boolean) classifier.invoke(null, "Player: " + line)) throw new AssertionError("Player message filtered");
		var messagesField = ChatMessageFilter.class.getDeclaredField("SCRAPPY_MESSAGES");
		messagesField.setAccessible(true);
		var messages = (java.util.Set<?>) messagesField.get(null);
		if (!messages.contains("The Scrappy happily eats the Wholesale Wheat.")) throw new AssertionError("Missing Scrappy feedback");
		if (!messages.contains("The Scrappy happily eats the Flavor-packed Fish.")) throw new AssertionError("Missing Scrappy feedback");
		if (!messages.contains("The Scrappy happily eats the Lush Lily Pad.")) throw new AssertionError("Missing Scrappy feedback");
		if (!messages.contains("[NPC] Hunter Jasper: Hmm that \"Hunter\" Tobias guy was right... Nice Catch!")) throw new AssertionError("Missing Scrappy feedback");
		if (!messages.contains("[NPC] Hunter Jasper: There might be more Scrappy around - I wonder if my brothers have found them yet.")) throw new AssertionError("Missing Scrappy feedback");
		if (!messages.contains("[NPC] Hunter Luigi: That really worked, you caught the Scrappy! Nice going!")) throw new AssertionError("Missing Scrappy feedback");
		if (!messages.contains("[NPC] Hunter Luigi: My brothers might be around here, maybe they found more Scrappy...")) throw new AssertionError("Missing Scrappy feedback");
		if (!messages.contains("[NPC] Hunter Reeves: You really managed to catch that Scrappy? Nice going!")) throw new AssertionError("Missing Scrappy feedback");
		if (!messages.contains("[NPC] Hunter Reeves: Keep an eye out for my brothers - they might have found more Scrappy around...")) throw new AssertionError("Missing Scrappy feedback");
		return 13;
	}
	private ChatMessageFilterRegression() { }
}
