package com.atex;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

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
     * The template file used to produce the 'whatsnew.html', default ${pojrect.directory}/src/main/whatsnew/whatsnew.html
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/whatsnew/whatsnew.html", property = "templateFile")
    private File templateFile;

    /**
     * The base url to the jira installation (eg http://support.polopoly.com/jira)
     */
    @Parameter(defaultValue = "http://support.polopoly.com/jira", property = "jira.url")
    private String jiraUrl;

    /**
     * Field(s) to use as what changed note (the first non empty is used), comma separated list.
     */
    @Parameter(defaultValue = "customfield_10068,summary", property = "jira.fields")
    private String fields;

    /**
     * Field(s) to exclude, because sometimes jira does not allow negation in searches...
     */
    @Parameter(defaultValue = "customfield_10067=Exclude release note", property = "jira.excludes")
    private String excludes;

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
        Splitter splitter = Splitter.on(',').trimResults().omitEmptyStrings();;
        client.fields = ImmutableList.copyOf(splitter.splitToList(fields));
        client.excludes = ImmutableMap.copyOf(parseExcludes(splitter.splitToList(excludes)));
        client.version = stripSnapshot(version);
        new WhatsNewTemplate(outputDirectory, templateFile, client.changes()).write();
    }

    private Map<String, String> parseExcludes(List<String> excludes) throws MojoExecutionException
    {
        Map<String, String> map = Maps.newHashMap();
        for (String exclude : excludes) {
            int index = exclude.indexOf('=');
            if (index == -1) {
                throw new MojoExecutionException("Illegal exclude pattern: " + exclude);
            }
            map.put(exclude.substring(0, index), exclude.substring(index+1));
        }
        return map;
    }

    private String stripSnapshot(String version) {
        if (version.endsWith("-SNAPSHOT")) {
            return version.substring(0, version.length() - "-SNAPSHOT".length());
        }
        return version;
    }
}
