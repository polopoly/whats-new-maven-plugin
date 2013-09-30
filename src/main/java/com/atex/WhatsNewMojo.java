package com.atex;

import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

@Mojo(name = "whats-new", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, requiresOnline = true, requiresProject = true, threadSafe = true)
public class WhatsNewMojo
    extends AbstractMojo
{
    @Parameter(defaultValue = "${project.build.directory}/generated-resources", property = "outputDir")
    private File outputDirectory;

    @Parameter(defaultValue = "http://support.polopoly.com/jira/rest/api/2.0.alpha1", property = "jira.url")
    private String jiraUrl;

    @Parameter(defaultValue = "ART", property = "jira.project")
    private String project;

    @Parameter(defaultValue = "summary", property = "jira.field")
    private String fields;

    @Parameter(defaultValue = "jira", property = "jira.id")
    private String jiraId;

    @Parameter(defaultValue = "${settings}")
    private Settings settings;

    public void execute() throws MojoExecutionException
    {
        if (settings == null) {
            throw new MojoExecutionException("No settings");
        }
        Server server = settings.getServer(jiraId);
        if (server == null) {
            throw new MojoExecutionException(String.format("No server '%s' in settings", jiraId));
        }
        WhatsNewJiraClient client = new WhatsNewJiraClient(jiraUrl, server.getUsername(), server.getPassword());
        client.project = project;
        client.fields = ImmutableList.copyOf(Splitter.on(',').trimResults().omitEmptyStrings().splitToList(fields));
        System.out.println(client.changes());
    }
}
