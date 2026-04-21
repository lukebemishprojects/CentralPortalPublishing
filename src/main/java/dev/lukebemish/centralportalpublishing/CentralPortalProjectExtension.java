package dev.lukebemish.centralportalpublishing;

import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.api.tasks.bundling.Zip;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Map;

public abstract class CentralPortalProjectExtension {
    static final String CONSUMES_BUNDLE_UPLOAD_DEPENDENCIES = "_centralPortalPublishingConsumesBundleUploadDependencies";
    static final String CONSUMES_BUNDLE_UPLOAD = "_centralPortalPublishingConsumesBundleUpload";
    static final String BUNDLE_GROUP = "dev.lukebemish.central-portal-publishing.internal.uploads-bundle";

    @Inject
    public CentralPortalProjectExtension() {
        getProject().getPluginManager().withPlugin("publishing", ignored -> {
            var repositories = getProject().getExtensions().getByType(PublishingExtension.class).getRepositories();
            var extensions = (ExtensionAware) repositories;
            extensions.getExtensions().add(CentralPortalRepositoryHandlerExtension.class, "centralPortalPublishing", getObjects().newInstance(CentralPortalRepositoryHandlerExtension.class, repositories));
        });
    }

    @Inject
    protected abstract ObjectFactory getObjects();

    @Inject
    protected abstract Project getProject();

    static String taskValue(String project, String name) {
        var parts = project.split(":");
        StringBuilder taskName = new StringBuilder();
        boolean isFirst = true;
        for (var part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (isFirst) {
                isFirst = false;
                taskName.append(part);
            } else {
                taskName.append(StringUtils.capitalize(part));
            }
        }
        if (isFirst) {
            taskName.append(name);
        } else {
            taskName.append(StringUtils.capitalize(name));
        }
        return taskName.toString();
    }

    static String capabilityModule(String project) {
        var parts = project.split(":");
        StringBuilder value = new StringBuilder(BUNDLE_GROUP + ".");
        boolean isFirst = true;
        for (var part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (isFirst) {
                isFirst = false;
                value.append(part);
            } else {
                value.append(".");
                value.append(part);
            }
        }
        return value.toString();
    }

    static String capabilityGroup(String project, String name) {
        return capabilityModule(project) +
            ".." +
            name;
    }

    public void bundle(String name, Action<? super CentralPortalBundleSpec> action) {
        getProject().getPluginManager().apply("publishing");
        String capabilityGroup = capabilityGroup(getProject().getPath(), name);
        var depConfiguration = getProject().getConfigurations().dependencyScope(CONSUMES_BUNDLE_UPLOAD_DEPENDENCIES + StringUtils.capitalize(name));
        var configuration = getProject().getConfigurations().resolvable(CONSUMES_BUNDLE_UPLOAD + StringUtils.capitalize(name), config -> {
            config.getAttributes().attribute(CentralPortalPublishingPlugin.UPLOADS_BUNDLE, true);
            config.extendsFrom(depConfiguration.get());
            config.setTransitive(false);
        });
        getProject().getRootProject().getAllprojects().forEach(p -> {
            var isolated = p.getIsolated();
            var dependency = getProject().getDependencies().project(Map.of(
                "path", isolated.getPath()
            ));
            dependency = ((ProjectDependency) dependency).capabilities(c -> c.requireCapability(capabilityGroup + ":" + capabilityModule(isolated.getPath())));
            getProject().getDependencies().add(depConfiguration.getName(), dependency);
        });
        var bundleDependencies = getProject().files();
        var artifacts = configuration.map(config -> config.getIncoming().artifactView(view -> {
            view.setLenient(true); // So that we act as expected with projects that do not use this bundle
            view.withVariantReselection();
            view.getAttributes().attribute(CentralPortalPublishingPlugin.UPLOADS_BUNDLE, true);
        }).getArtifacts());
        bundleDependencies.from(artifacts.flatMap(ArtifactCollection::getResolvedArtifacts).map(set -> {
            var files = new ArrayList<File>();
            for (var resolved : set) {
                files.add(resolved.getFile());
            }
            return files;
        }));
        bundleDependencies.builtBy(artifacts.map(ArtifactCollection::getArtifactFiles));
        var makeBundle = getProject().getTasks().register("make"+StringUtils.capitalize(name)+"CentralPortalBundle", Zip.class, task -> {
            task.getDestinationDirectory().set(getProject().getLayout().getBuildDirectory().dir("centralPortalPublishing/bundles"));
            task.getArchiveFileName().set(name+".zip");
            task.dependsOn(bundleDependencies);
            task.from(bundleDependencies.getAsFileTree(), spec -> {
                spec.exclude("**/maven-metadata.xml");
                spec.exclude("**/maven-metadata.xml.*");
            });
        });
        var publishBundle = getProject().getTasks().register("publish"+StringUtils.capitalize(name)+"CentralPortalBundle", UploadBundleTask.class, task -> {
            task.getInputs().files(bundleDependencies.getAsFileTree().filter(f -> !f.getName().equals("maven-metadata.xml") && !f.getName().startsWith("maven-metadata.xml."))).skipWhenEmpty();
            task.setGroup("publishing");
            action.execute(task.getBundleSpec());
            task.getBundleFile().set(makeBundle.flatMap(Zip::getArchiveFile));
            task.dependsOn(makeBundle);
        });
        getProject().getTasks().named(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME, task -> {
            task.dependsOn(publishBundle);
        });
    }
}
