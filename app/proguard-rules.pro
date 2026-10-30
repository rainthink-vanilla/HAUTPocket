# No JavaScript native bridge is used.
# Existing WorkManager records from debug versions contain this fully qualified class name.
-keep class cn.hautpocket.app.data.DailyElectricityWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
