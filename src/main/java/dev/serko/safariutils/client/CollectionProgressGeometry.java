package dev.serko.safariutils.client;

/** Packed, ordered overlay geometry. Each frame owns its array until GUI rendering finishes. */
final class CollectionProgressGeometry {
    record Frame(int[] quads, int length) { }
    private CollectionProgressGeometry() { }

    static Frame overlay(int filled, int height, long now) {
        if (filled <= 0 || height <= 0) return new Frame(new int[0], 0);
        int x=0, y=0;
        // At most two wave quads per sample/row, two per glimmer, and one edge.
        int[] quads=new int[(Math.min(192,filled)*height*2+19)*5];
        int cursor=0;
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
                if(shade>0) cursor=quad(quads,cursor,left,y+row,right,y+row+1,(shade<<24)|0x38266B);
                if(light>0) cursor=quad(quads,cursor,left,y+row,right,y+row+1,(light<<24)|0xF2FFFF);
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
            cursor=quad(quads,cursor,Math.max(x,px-1),cy,Math.min(x+filled,px+2),cy+1,(alpha/5<<24)|0xF6E9FF);
            cursor=quad(quads,cursor,px,cy,px+1,cy+1,(alpha<<24)|0xFFFFFF);
        }
        int edgeAlpha=100+(int)(55*(1+Math.sin(phase*Math.PI*4)));
        cursor=quad(quads,cursor,x+filled-1,y,x+filled,y+height,(edgeAlpha<<24)|0xEEFFFF);

        return new Frame(quads,cursor);
    }

    private static int quad(int[] quads,int cursor,int left,int top,int right,int bottom,int colour) {
        quads[cursor++]=left;
        quads[cursor++]=top;
        quads[cursor++]=right;
        quads[cursor++]=bottom;
        quads[cursor++]=colour;
        return cursor;
    }
}
