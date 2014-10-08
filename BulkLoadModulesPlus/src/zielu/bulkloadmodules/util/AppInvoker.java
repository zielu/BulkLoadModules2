/*
 * $Id$
 */
package zielu.bulkloadmodules.util;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * @author Lukasz Zielinski
 */
public class AppInvoker {

    private final Application application;

    private AppInvoker(Application _application) {
        application = _application;
    }

    public static AppInvoker create(Application application) {
        return new AppInvoker(application);
    }

    public static AppInvoker get() {
        return create(ApplicationManager.getApplication());
    }

    public <T> T invokeAndWait(Callable<T> action) {
        FutureTask<T> task = new FutureTask<T>(action);
        application.invokeAndWait(task, application.getAnyModalityState());
        try {
            return task.get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public void invokeAndWait(Runnable action) {
        application.invokeAndWait(action, application.getAnyModalityState());
    }

    public void runWriteActionAndWait(Runnable action) {
        runWriteActionAndWait(action, application.getAnyModalityState());
    }

    public void runWriteActionAndWait(Runnable action, ModalityState modality) {
        application.invokeAndWait(new WriteAction(application, action), modality);
    }

    public void saveSettings() {
        runWriteActionAndWait(new Runnable() {
            @Override
            public void run() {
                application.saveSettings();
            }
        });
    }

    private class WriteAction implements Runnable {
        private final Application application;
        private final Runnable action;

        private WriteAction(Application application, Runnable action) {
            this.application = application;
            this.action = action;
        }

        @Override
        public void run() {
            application.runWriteAction(action);
        }
    }
}
