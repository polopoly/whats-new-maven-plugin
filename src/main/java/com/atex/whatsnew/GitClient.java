package com.atex.whatsnew;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.logging.Log;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

public class GitClient
{
    final File gitDir;
    final String projectKey;
    final Log log;

    private Map<String, String> cache;

    private ObjectId resolvedHeadObjectId = null;
    private String resolvedHeadBranchName = null;

    public GitClient(File gitDir, String projectKey, Log log) {
        this.gitDir = gitDir;
        this.projectKey = projectKey;
        this.log = log;
    }

    public ObjectId getResolvedHeadObjectId() {
        if (resolvedHeadObjectId == null) {
            cache = readGit();
        }
        return resolvedHeadObjectId;
    }

    public String getResolvedHeadBranchName() {
        if (resolvedHeadBranchName == null) {
            cache = readGit();
        }
        return resolvedHeadBranchName;
    }

    public String dateOf(String id) {
        if (cache == null) {
            cache = readGit();
        }
        return cache.get(id);
    }

    public boolean hasId(String input) {
        if (cache == null) {
            cache = readGit();
        }
        return cache.containsKey(input);
    }

    Map<String, String> readGit() {
        try {
            log("Reading HEAD from %s for project %s", gitDir.getAbsolutePath(), projectKey);

            Git git = Git.open(gitDir);

            Ref headRef = git.getRepository().getRef("HEAD");

            resolvedHeadObjectId = headRef.getObjectId();

            String headRefName = headRef.getTarget().getName();
            resolvedHeadBranchName = (headRefName.startsWith("refs/heads/")) ? headRefName.substring("refs/heads/".length()) : headRefName;

            SimpleDateFormat sf = new SimpleDateFormat("yyyy-MM-dd");
            Map<String, String> result = Maps.newHashMap();

            for (RevCommit rc : git.log().add(resolvedHeadObjectId).setMaxCount(2000).call()) {
                String msg = rc.getShortMessage();
                String key = keyOf(msg.trim());
                if (key != null) {
                    if (!result.containsKey(key)) {
                        if (rc.getAuthorIdent() != null && rc.getAuthorIdent().getWhen() != null) {
                            String date = sf.format(rc.getAuthorIdent().getWhen());
                            log("Key %s found first at %s", key, date);
                            result.put(key, date);
                        }
                    }
                } else {
                    log("Found no key in %s", msg.trim());
                }
            }
            return ImmutableMap.copyOf(result);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (NoHeadException e) {
            throw new RuntimeException(e);
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }
    }

    private String keyOf(String msg) {
        Pattern p = Pattern.compile(projectKey + "-\\d+");
        Matcher m = p.matcher(msg);
        if (m.find()) {
            return m.group();
        }
        return null;
    }

    private void log(String format, String... args) {
        if (log != null) {
            if (log.isDebugEnabled()) {
                log.debug(String.format(format, (Object[]) args));
            }
        }
    }
}
