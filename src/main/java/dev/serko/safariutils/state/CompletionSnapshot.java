package dev.serko.safariutils.state;

import com.google.gson.*;
import java.util.*;

/** Immutable complete reporter evidence. Transport ages use the local monotonic clock. */
public final class CompletionSnapshot {
    public static final long TTL = 45_000;
    private final int[][] rows;
    public CompletionSnapshot(int[][] rows) {
        if(rows.length!=37)throw new IllegalArgumentException("species count");
        this.rows=new int[37][3];
        for(int i=0;i<37;i++) {
            if(rows[i].length!=3)throw new IllegalArgumentException("row shape");
            for(int j=0;j<3;j++) {
                int value=rows[i][j];if(value<0 || value>(j==0?7:256))throw new IllegalArgumentException("row bound");
                this.rows[i][j]=value;
            }
        }
    }
    public int value(int species,int column) { return rows[species][column]; }
    public String payload() { JsonObject value=new JsonObject();value.addProperty("version",1);value.add("rows",new Gson().toJsonTree(rows));return value.toString(); }
    public static CompletionSnapshot parse(String text,String expectedRun) {
        try {
            if(text==null || text.length()>3072)return null;
            var root=JsonParser.parseString(text).getAsJsonObject();
            if(root.get("version").getAsBigDecimal().intValueExact()!=1 || !expectedRun.equals(root.get("runId").getAsString()))return null;
            UUID.fromString(root.get("visitId").getAsString());
            var array=root.getAsJsonArray("rows");if(array.size()!=37)return null;
            int[][] rows=new int[37][3];
            for(int i=0;i<37;i++) {
                var row=array.get(i).getAsJsonArray();if(row.size()!=3)return null;
                for(int j=0;j<3;j++)rows[i][j]=row.get(j).getAsBigDecimal().intValueExact();
            }
            return new CompletionSnapshot(rows);
        } catch(RuntimeException malformed) { return null; }
    }
    public static final class Store {
        private record Evidence(CompletionSnapshot snapshot,long expires) { }
        private final Map<String,Evidence> reporters=new HashMap<>();
        public void clear() { reporters.clear(); }
        public void accept(String reporter,CompletionSnapshot snapshot,long age,long now) {
            if(snapshot==null || age<0 || age>=TTL)return;
            if(reporters.size()>=4 && !reporters.containsKey(reporter))return;
            reporters.put(reporter,new Evidence(snapshot,now+TTL-age));
        }
        public int value(int species,int column,long now) {
            reporters.values().removeIf(e->e.expires<=now);
            int combined=0;
            for(var evidence:reporters.values())combined=column==0?combined|evidence.snapshot.value(species,column):Math.max(combined,evidence.snapshot.value(species,column));
            return combined;
        }
        public int size(long now) { reporters.values().removeIf(e->e.expires<=now);return reporters.size(); }
    }
}
