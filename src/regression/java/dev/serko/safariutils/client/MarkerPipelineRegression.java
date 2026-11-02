package dev.serko.safariutils.client;

/** Validates actual pipeline descriptors without requiring a live GPU draw. */
public final class MarkerPipelineRegression {
    public static int run() throws Exception {
        Class<?> pipeline;
        try { pipeline=Class.forName("com.mojang.renderpearl.api.pipeline.RenderPipeline"); }
        catch(ClassNotFoundException legacyRenderer) { return 0; }
        int checks=0;
        for(var field:MarkerPipelines.class.getDeclaredFields()) {
            if(!pipeline.isAssignableFrom(field.getType()))continue;
            field.setAccessible(true);
            Object descriptor=field.get(null);
            var targets=(java.util.List<?>)pipeline.getMethod("getColorTargetStates").invoke(descriptor);
            if(targets.size()!=1 || targets.getFirst()==null)
                throw new AssertionError("Marker pipeline must match single-color world pass: "+field.getName()+" has "+targets.size()+" color targets");
            checks++;
        }
        if(checks<6)throw new AssertionError("Missing marker pipeline descriptor coverage");
        return checks;
    }
}
