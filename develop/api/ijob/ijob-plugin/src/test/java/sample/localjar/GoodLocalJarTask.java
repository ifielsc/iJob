package sample.localjar;

import com.ifielsc.ijob.spi.localjar.LocalJarContext;
import com.ifielsc.ijob.spi.localjar.LocalJarExecution;
import com.ifielsc.ijob.spi.localjar.LocalJarResult;
import com.ifielsc.ijob.spi.localjar.LocalJarTask;

public final class GoodLocalJarTask implements LocalJarTask {
    private volatile boolean stopped;
    private volatile boolean resourceClosed;

    public GoodLocalJarTask() {}

    @Override
    public LocalJarResult run(LocalJarExecution execution, LocalJarContext context)
            throws Exception {
        context.putVariable("external.configuration", execution.configuration());
        if (execution.configuration().contains("\"fail\":true")) {
            throw new IllegalStateException("external failure details");
        }
        if (execution.configuration().contains("\"resource\":true")) {
            context.registerResource(
                    () -> {
                        resourceClosed = true;
                        context.putVariable("external.resourceClosed", true);
                    });
            context.putVariable("external.resourceRegistered", true);
        }
        while (execution.configuration().contains("\"block\":true") && !stopped) {
            context.checkExecution();
            Thread.sleep(5);
        }
        return stopped
                ? LocalJarResult.stopped("stopped by hook")
                : LocalJarResult.success("external success");
    }

    @Override
    public void stop(LocalJarExecution execution, LocalJarContext context) {
        context.putVariable("external.resourceClosedBeforeStop", resourceClosed);
        stopped = true;
    }
}
