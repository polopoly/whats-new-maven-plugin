package com.atex;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

public class WhatsNewGitClient
{
    final File gitDir;
    final String branch;
    final String projectKey;

    private Map<String, String> cache;

    public WhatsNewGitClient(File gitDir, String branch, String projectKey) {
        this.gitDir = gitDir;
        this.branch = branch;
        this.projectKey = projectKey;
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
            Git git = Git.open(gitDir);
            ObjectId branchId = git.getRepository().resolve(branch);
            SimpleDateFormat sf = new SimpleDateFormat("yyyy-MM-dd");
            Map<String, String> result = Maps.newHashMap();
            for (RevCommit rc : git.log().add(branchId).setMaxCount(2000).call()) {
                String msg = rc.getShortMessage();
                String key = keyOf(msg.trim());
                if (key != null && !result.containsKey(key)) {
                    if (rc.getAuthorIdent() != null && rc.getAuthorIdent().getWhen() != null) {
                        result.put(key, sf.format(rc.getAuthorIdent().getWhen()));
                    }
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
}
