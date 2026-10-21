# ML Kit discovers Firebase component registrars from AndroidManifest metadata and
# invokes their no-argument constructors through reflection. The older consumer
# rule in firebase-components keeps the classes but not these constructors in R8
# full mode, leaving ML Kit's component container empty at app startup.
-keep,allowoptimization class * implements com.google.firebase.components.ComponentRegistrar {
    public <init>();
}
