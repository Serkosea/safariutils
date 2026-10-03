package dev.serko.safariutils.client;

import dev.serko.safariutils.session.RunDataIntegrity;
import net.minecraft.client.Minecraft;

/** Direct actions used by the Data settings tabs without an intermediary screen. */
final class DataActions {
	private static final long STATUS_MILLIS = 3_000L;
	private static SettingsTransfer.Preview pendingImport;
	private static String pendingCode = "";
	private static String status = "";
	private static ClientMessages.Tone statusTone = ClientMessages.Tone.INFO;
	private static long statusUntil;
	private static long importArmedUntil;
	private static long repairArmedUntil;

	private DataActions() { }

	static String status() {
		return System.currentTimeMillis() < statusUntil ? status : "";
	}

	static ClientMessages.Tone statusTone() { return statusTone; }
	static void clearStatus() {
		statusUntil = 0L;
		pendingImport = null;
		pendingCode = "";
		importArmedUntil = 0L;
		repairArmedUntil = 0L;
	}

	private static void status(String message, ClientMessages.Tone tone) {
		status = message;
		statusTone = tone;
		statusUntil = System.currentTimeMillis() + STATUS_MILLIS;
	}

	static boolean importSettings() {
		String code = Minecraft.getInstance().keyboardHandler.getClipboard().strip();
		long now = System.currentTimeMillis();
		if (pendingImport == null || !pendingImport.valid() || now >= importArmedUntil
				|| !code.equals(pendingCode)) {
			pendingImport = SettingsTransfer.preview(code);
			pendingCode = code;
			if (!pendingImport.valid()) {
				importArmedUntil = 0L;
				status(pendingImport.message(), ClientMessages.Tone.ERROR);
				return false;
			}
			status("Valid code from " + pendingImport.sourceVersion()
				+ " — click Import Settings again to confirm", ClientMessages.Tone.WARNING);
			importArmedUntil = statusUntil;
			return false;
		}
		SettingsTransfer.ImportResult result = SettingsTransfer.apply(pendingImport);
		String message = result.success()
			? "Settings imported; Previous settings were backed up" : result.message();
		status(message, result.success() ? ClientMessages.Tone.SUCCESS : ClientMessages.Tone.ERROR);
		pendingImport = null;
		pendingCode = "";
		importArmedUntil = 0L;
		if (result.success()) ClientMessages.send(message, ClientMessages.Tone.SUCCESS);
		return result.success();
	}

	static void exportSettings() {
		try {
			String code = SettingsTransfer.exportCode();
			Minecraft.getInstance().keyboardHandler.setClipboard(code);
			String message = "Exported settings to clipboard (" + code.length() + " characters)";
			status(message,
				ClientMessages.Tone.SUCCESS);
			ClientMessages.send(message, ClientMessages.Tone.SUCCESS);
		} catch (RuntimeException failed) {
			OperationalLog.error("CONFIG/EXPORT", failed);
			status("Could not export settings", ClientMessages.Tone.ERROR);
		}
	}

	static void checkRunHistory() {
		RunDataIntegrity.Audit audit = RunDataIntegrity.audit();
		status(audit.runs() + " saved runs — " + audit.message(), audit.healthy()
			? ClientMessages.Tone.SUCCESS
			: audit.readable() ? ClientMessages.Tone.WARNING : ClientMessages.Tone.ERROR);
	}

	static void repairRunHistory() {
		RunDataIntegrity.Audit audit = RunDataIntegrity.audit();
		if (!audit.readable()) {
			status(audit.message(), ClientMessages.Tone.ERROR);
			return;
		}
		if (audit.repairable() == 0) {
			status("Run history is already healthy", ClientMessages.Tone.SUCCESS);
			return;
		}
		long now = System.currentTimeMillis();
		if (now >= repairArmedUntil) {
			status(audit.issues() + " issue" + (audit.issues() == 1 ? "" : "s")
				+ " found — click Repair Run History again to confirm", ClientMessages.Tone.WARNING);
			repairArmedUntil = statusUntil;
			return;
		}
		repairArmedUntil = 0L;
		RunDataIntegrity.Repair result = RunDataIntegrity.repair();
		status(result.message(), result.success()
			? ClientMessages.Tone.SUCCESS : ClientMessages.Tone.ERROR);
	}
}
