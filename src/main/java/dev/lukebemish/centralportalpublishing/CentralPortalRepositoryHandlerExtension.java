package dev.lukebemish.centralportalpublishing;

import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository;

import javax.inject.Inject;
import java.util.List;

public abstract class CentralPortalRepositoryHandlerExtension {
    private final RepositoryHandler delegate;
    static final String UPLOADS_BUNDLE_CONFIGURATION = "_centralPortalPublishingUploadsBundle";

    @Inject
    public CentralPortalRepositoryHandlerExtension(RepositoryHandler delegate) {
        this.delegate = delegate;
    }

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    @Inject
    protected abstract Project getProject();

    public MavenArtifactRepository centralSnapshots() {
        return centralSnapshots(r -> {});
    }

    public MavenArtifactRepository centralSnapshots(Action<? super MavenArtifactRepository> action) {
        var repo = delegate.maven(r -> {
            r.setUrl("https://central.sonatype.com/repository/maven-snapshots/ ");
        });
        action.execute(repo);
        return repo;
    }

    public void portalBundle(String path, String name) {
        var fullName = CentralPortalProjectExtension.taskValue(path, name);
        var capabilityGroup = CentralPortalProjectExtension.capabilityGroup(path, name);
        var capabilityName = CentralPortalProjectExtension.capabilityModule(this.getProject().getPath());
        var repoDirectory = getProjectLayout().getBuildDirectory().dir("centralPortalPublishing/repositories/"+fullName);

        var repo = delegate.maven(r -> {
            r.setName("centralPortal"+StringUtils.capitalize(fullName));
            r.setUrl(repoDirectory.get().getAsFile().toURI());
        });

        var clearRepository = getProject().getTasks().register("clearCentralPortal" + StringUtils.capitalize(fullName) + "Repository", ClearRepositoryTask.class, task -> {
            task.getRepositoryDirectory().set(repoDirectory);
        });

        getProject().getTasks().withType(PublishToMavenRepository.class, task -> {
            task.dependsOn(getProject().provider(() -> {
                var typed = getProject().getTasks().named(task.getName(), PublishToMavenRepository.class).get();
                if (typed.getRepository() != null && typed.getRepository().getName().equals(repo.getName())) {
                    return List.of(clearRepository);
                }
                return List.of();
            }));
        });

        var publishTask = getProject().getTasks().named("publishAllPublicationsToCentralPortal" + StringUtils.capitalize(fullName) +"Repository");
        var outgoing = getProject().getConfigurations().consumable(UPLOADS_BUNDLE_CONFIGURATION + StringUtils.capitalize(fullName), config -> {
            config.getAttributes().attribute(CentralPortalPublishingPlugin.UPLOADS_BUNDLE, true);
            config.getOutgoing().capability(capabilityGroup + ":" + capabilityName + ":1");
        });
        getProject().getArtifacts().add(outgoing.getName(), repoDirectory, artifact -> {
            artifact.builtBy(publishTask);
        });
    }
}
