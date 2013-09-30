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
/**
 * Generates a 'whatsnew.html' from all Closed and Resolved issues from a jira project. Possibly including attached image resources as screenshots.
 */
public class WhatsNewMojo
    extends AbstractMojo
{
    /**
     * The target directory of the generated whats new page, contains the main 'whatsnew.html' and possible
     * image resources in directory 'whatsnew-img'.
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-resources", property = "outputDir")
    private File outputDirectory;

    /**
     * The base url to the jira installation (eg http://support.polopoly.com/jira)
     */
    @Parameter(defaultValue = "http://support.polopoly.com/jira", property = "jira.url")
    private String jiraUrl;

    /**
     * The field(s) to use as what changed note (the first non empty is used), comma separated list.
     */
    @Parameter(defaultValue = "summary", property = "jira.fields")
    private String fields;

    /**
     * The id of the server in ~/.m2/settings.xml to use for username/password to login to the jira instance.
     */
    @Parameter(defaultValue = "jira", property = "jira.server-id")
    private String jiraId;

    /**
     * The jira project key, default 'ART'.
     */
    @Parameter(defaultValue = "ART", property = "jira.project-key")
    private String project;

    /**
     * The jira version, default '{project.version}' without possibly '-SNAPSHOT'.
     */
    @Parameter(defaultValue = "${project.version}", property = "jira.project-version")
    private String version;

    /**
     * The settings bean, not configurable (Do not touch).
     */
    @Parameter(defaultValue = "${settings}", readonly = true)
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
        WhatsNewJiraClient client = new WhatsNewJiraClient(jiraUrl + "/rest/api/2.0.alpha1", server.getUsername(), server.getPassword());
        if (getLog().isDebugEnabled()) {
            client.log = getLog();
        }
        client.project = project;
        client.fields = ImmutableList.copyOf(Splitter.on(',').trimResults().omitEmptyStrings().splitToList(fields));
        client.version = stripSnapshot(version);
        for (String change : client.changes()) {
            System.out.println(change);
        }
    }

    private String stripSnapshot(String version) {
        if (version.endsWith("-SNAPSHOT")) {
            return version.substring(0, version.length() - "-SNAPSHOT".length());
        }
        return version;
    }
}
