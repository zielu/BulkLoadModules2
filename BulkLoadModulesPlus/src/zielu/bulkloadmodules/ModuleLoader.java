package zielu.bulkloadmodules;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zielu.bulkloadmodules.util.AppInvoker;

public class ModuleLoader {
    private final Logger LOG = Logger.getInstance(getClass());

    private final Project project;

    private ModuleLoader(Project _project) {
        project = _project;
    }

    public static ModuleLoader create(@NotNull Project project) {
        return new ModuleLoader(Preconditions.checkNotNull(project));
    }

    public void loadModules() {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                                               .withTitle("Select modules directory");
        FileChooserDialog chooser = FileChooserFactory.getInstance()
                                        .createFileChooser(descriptor, project, null);
        VirtualFile[] directories = chooser.choose(project);
        ImmutableCollection<VirtualFile> files = scanDirectories(directories);
        loadImlFiles(files);
    }

    private void loadImlFiles(final ImmutableCollection<VirtualFile> files) {
        new Task.Modal(project, "Loading modules", false) {
            private double current;

            @Nullable
            @Override
            public NotificationInfo getNotificationInfo() {
                return new NotificationInfo("BulkLoadModules", "Loading modules",
                                               "Bulk added "+((int) current)+" modules");
            }

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                AppInvoker appInvoker = AppInvoker.get();
                double total = files.size();
                for (VirtualFile file : files) {
                    indicator.setFraction(current / total);
                    indicator.setText("Loading " + file.getPath());
                    appInvoker.runWriteActionAndWait(loadFileAction(file));
                    current++;
                }
            }
        }.queue();
    }

    private Runnable loadFileAction(final VirtualFile imlFile) {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    ModuleManager.getInstance(project).loadModule(imlFile.getPath());
                } catch (Exception e) {
                    LOG.warn("Could not load module from "+imlFile.getPath(), e);
                }
            }
        };
    }

    private ImmutableCollection<VirtualFile> scanDirectories(VirtualFile[] directories) {
        ImlCollector collector = new ImlCollector();
        for (VirtualFile dir : directories) {
            scanDirectory(Paths.get(VfsUtil.toUri(dir)), collector);
        }
        return collector.getImlFiles();
    }

    private void scanDirectory(Path directory, ImlCollector collector) {
        try {
            java.nio.file.Files.walkFileTree(directory, collector);
        } catch (IOException e) {
            LOG.warn("Cannot walk "+directory, e);
        }
    }

    private static class ImlCollector extends SimpleFileVisitor<Path> {
        private final ImmutableList.Builder<VirtualFile> imlFiles = ImmutableList.builder();

        public ImmutableCollection<VirtualFile> getImlFiles() {
            return imlFiles.build();
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (attrs.isRegularFile()) {
                String ext = Files.getFileExtension(file.getFileName().toString());
                if ("iml".equals(ext)) {
                    imlFiles.add(VfsUtil.findFileByIoFile(file.toFile(), true));
                }
            }
            return super.visitFile(file, attrs);
        }
    }
}
