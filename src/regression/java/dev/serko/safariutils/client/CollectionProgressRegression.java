package dev.serko.safariutils.client;

import java.util.ArrayList;
import java.util.Arrays;

/** Visual-equivalence oracle for the pre-batching progress-bar draw sequence. */
public final class CollectionProgressRegression {
    public static int run() {
        int checks=0;
        for(int width:new int[]{1,17,191,192,529,612}) {
            for(int height:new int[]{1,4,7}) {
                for(long time:new long[]{0,743,2100,5999,6000,987654321}) {
                    var actual=CollectionProgressGeometry.overlay(width,height,time);
                    int[] expected=reference(width,height,time);
                    if(!Arrays.equals(expected,Arrays.copyOf(actual.quads(),actual.length())))
                        throw new AssertionError("Progress geometry changed: "+width+"/"+height+"/"+time);
                    for(int i=0;i<actual.length();i+=5) {
                        if(actual.quads()[i]<0 || actual.quads()[i+1]<0
                                || actual.quads()[i+2]>width || actual.quads()[i+3]>height)
                            throw new AssertionError("Progress overlay outside bar");
                    }
                    // Deferred render states must not be mutated by another extraction.
                    int[] snapshot=actual.quads().clone();
                    CollectionProgressGeometry.overlay(width,height,time+1);
                    if(!Arrays.equals(snapshot,actual.quads())) throw new AssertionError("Mutable frame reuse");
                    checks++;
                }
            }
        }
        if(CollectionProgressGeometry.overlay(0,4,0).length()!=0) throw new AssertionError("Empty bar");
        return checks+1;
    }
    private static int[] reference(int filled,int height,long now) {
        int x=0,y=0;
        var reference=new RecordingFills();
        double phase=(now%6000L)/6000.0;
        // Sample a genuine wave rather than a full-height brightness gradient.
        // Fractional row coverage crossfades adjacent pixels instead of snapping
        // the ribbon up/down. A dark underside keeps it readable on the pale fill.
        int segments=Math.min(192,filled);
        for(int i=0;i<segments;i++) {
            double position=(i+0.5)/segments;
            double wave=(height-1)*(0.5+0.45*Math.sin((position*1.75-phase)*Math.PI*2));
            int left=x+i*filled/segments, right=x+(i+1)*filled/segments;
            double crest=0.75+0.25*Math.sin((position-phase)*Math.PI*2);
            for(int row=0;row<height;row++) {
                double distance=Math.abs(row-wave);
                int light=(int)(210*crest*Math.max(0,1-distance/1.25));
                int shade=(int)(65*Math.max(0,1-Math.abs(row-wave-1.2)));
                if(shade>0) reference.fill(left,y+row,right,y+row+1,(shade<<24)|0x38266B);
                if(light>0) reference.fill(left,y+row,right,y+row+1,(light<<24)|0xF2FFFF);
            }
        }
        // Independent deterministic glimmers: each has its own lifetime, position
        // and brightness. Positions only change while faded out, never jitter each frame.
        for(int star=0;star<9;star++) {
            long period=2100L+star*317L;
            long clock=now+star*743L;
            long cycle=Math.floorDiv(clock,period);
            double life=Math.floorMod(clock,period)/(double)period;
            long seed=(cycle*0x9E3779B97F4A7C15L)^(star*0x632BE59BD9B4E019L);
            seed=(seed^(seed>>>30))*0xBF58476D1CE4E5B9L;
            seed=(seed^(seed>>>27))*0x94D049BB133111EBL;
            seed^=seed>>>31;
            int px=x+(int)Math.floorMod(seed,(long)filled);
            int cy=y+(int)Math.floorMod(seed>>>16,(long)height);
            double pulse=Math.pow(Math.sin(life*Math.PI),4);
            int alpha=(int)(235*pulse);
            if(alpha<4) continue;
            // A tiny bright core with faint lateral spill, never a large cross/block.
            reference.fill(Math.max(x,px-1),cy,Math.min(x+filled,px+2),cy+1,(alpha/5<<24)|0xF6E9FF);
            reference.fill(px,cy,px+1,cy+1,(alpha<<24)|0xFFFFFF);
        }
        int edgeAlpha=100+(int)(55*(1+Math.sin(phase*Math.PI*4)));
        reference.fill(x+filled-1,y,x+filled,y+height,(edgeAlpha<<24)|0xEEFFFF);

        return reference.values.stream().mapToInt(Integer::intValue).toArray();
    }
    private static final class RecordingFills {
        final ArrayList<Integer> values=new ArrayList<>();
        void fill(int left,int top,int right,int bottom,int colour) {
            values.add(left); values.add(top); values.add(right); values.add(bottom); values.add(colour);
        }
    }
}
