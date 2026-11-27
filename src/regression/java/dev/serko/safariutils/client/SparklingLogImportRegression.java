package dev.serko.safariutils.client;
import java.nio.file.*;
import java.util.zip.GZIPOutputStream;
public final class SparklingLogImportRegression {
 public static int run() throws Exception {
  Path root=Files.createTempDirectory("sparkling-log-test");
  String header="[12:00:00] [Render thread/INFO]: Setting user: Tester\n[12:00:01] [Render thread/INFO]: Connecting to mc.hypixel.net, 25565\n";
  String catchLine="[12:01:00] [Render thread/INFO]: [CHAT] SPARKLING! Tester caught a SPARKLING Flitter!\n";
  String log=header+catchLine+catchLine+catchLine.replace("12:01:00","12:02:00")
   +catchLine.replace("Tester caught","Other caught")
   +"[12:03:00] [Render thread/INFO]: Connecting to alpha.hypixel.net, 25565\n"
   +catchLine.replace("12:01:00","12:04:00");
  try {
   Files.writeString(root.resolve("2026-11-18-1.log"),log);
   try(var gz=new GZIPOutputStream(Files.newOutputStream(root.resolve("2026-11-18-2.log.gz")))) {gz.write(log.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
   Files.writeString(root.resolve("latest.log"),log);
   Files.writeString(root.resolve("2026-11-18-3.log"),catchLine);
   var result=SparklingLogImport.scan(root,"Tester");
   if(result.counts().getOrDefault("Flitter",0)!=2) throw new AssertionError("only unique personal main-server events: "+result);
   if(!SparklingLogImport.scan(root,"Different").counts().isEmpty())throw new AssertionError("account isolation");
   if(!SparklingLogImport.scan(root,"Tester").counts().equals(result.counts()))throw new AssertionError("repeat scan stability");
   Path extra=Files.createDirectory(root.resolve("logs"));
   Path archive=extra.resolve("2026-11-18-4.log");
   try {
    Files.writeString(archive,header+catchLine+catchLine.replace("12:01:00","12:05:00"));
    var both=SparklingLogImport.scan(java.util.List.of(root,SparklingLogImport.archiveFolder(root.toString()),extra),"Tester");
    if(both.counts().get("Flitter")!=3 || both.files()!=4)throw new AssertionError("combined archives: "+both);
   } finally {Files.deleteIfExists(archive);Files.delete(extra);}
   return 4;
  } finally {try(var files=Files.list(root)){for(Path f:files.toList())Files.delete(f);}Files.delete(root);}
 }
}
