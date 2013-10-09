package com.atex.whatsnew;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.IOUtil;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class JiraClient
{
    public String project = "ART";
    public String version = "2.0.0";
    public ImmutableList<String> fields = ImmutableList.of("summary");
    public ImmutableMap<String, String> excludes = ImmutableMap.of();

    final String url;
    final String user;
    final String pass; 
    final Log log;

    final CloseableHttpClient client;
    final UsernamePasswordCredentials creds;
    final BasicHttpContext ctx;
    final BasicScheme scheme;
    final Gson gson;

    public JiraClient(String url, String user, String pass, Log log) {
        this.url = url + "/rest/api/2.0.alpha1";
        this.user = user;
        this.pass = pass;
        this.log = log;

        client = HttpClientBuilder.create().build();
        creds = new UsernamePasswordCredentials(user, pass);
        ctx = new BasicHttpContext();
        scheme = new BasicScheme();
        gson = new GsonBuilder().create();
    }

    public static class SearchResult {
        public String startAt;
        public String maxResults;
        public String total;
        public List<SearchResultIssue> issues = new ArrayList<SearchResultIssue>();
    }

    public static class SearchResultIssue {
        public String self;
        public String key;
    }

    public static class IssueResult {
        public String key;
        public Map<String, JsonElement> fields = new HashMap<String, JsonElement>();
    }

    public static class IssueResultField {
        public String key;
        public String value;
    }

    public List<Change> changes(final Predicate<String> prefilter) {
        HttpGet get;
        try {
            URIBuilder builder = new URIBuilder(url + "/search");
            String jql = String.format("project = '%s' and fixVersion = '%s'", project, version);
            if (prefilter == null) {
                jql = jql + "and status in ('Closed', 'Resolved')";
            }
            builder.addParameter("jql", jql);
            builder.addParameter("maxResults", "100");
            if (log != null) {
                log.debug("SEARCH " + builder.build().toASCIIString());
            }
            get = new HttpGet(builder.build());           
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        HttpResponse response = null;
        try {
            get.addHeader(scheme.authenticate(creds, get, ctx));
            response = client.execute(get, ctx);
            InputStream stream = response.getEntity().getContent();
            if (log != null) {
                ByteArrayOutputStream copy = new ByteArrayOutputStream();
                IOUtil.copy(stream, copy);
                byte[] bytes = copy.toByteArray();
                log.debug("SEARCH RESPONSE " + new String(bytes, "UTF-8"));
                stream = new ByteArrayInputStream(bytes);
            }
            SearchResult res = gson.fromJson(new InputStreamReader(stream, "UTF-8"), SearchResult.class);
            Iterable<SearchResultIssue> issueIds = res.issues;
            if (prefilter != null) {
                issueIds = Iterables.filter(issueIds, new Predicate<SearchResultIssue>() {
                    public boolean apply(SearchResultIssue input) {
                        return prefilter.apply(input.key);
                    }
                });
            }
            Iterable<IssueResult> issues = Iterables.transform(issueIds, new Function<SearchResultIssue, IssueResult>() {
                public IssueResult apply(SearchResultIssue input) {
                    return getIssue(input.key);
                }
            });
            Iterable<IssueResult> included = Iterables.filter(issues, new Predicate<IssueResult>() {
                public boolean apply(IssueResult input) {
                    return filter(input);
                }
            });
            List<Change> result = Lists.newArrayList();
            for (IssueResult ir : included) {
                Change change = new Change();
                change.id = ir.key;
                change.date = dateOf(ir);
                change.change = describe(ir);
                change.previewUrl = previewOf(ir);
                if (change.previewUrl != null) {
                    change.preview = change.id + "." + suffix(change.previewUrl);
                }
                result.add(change);
            }
            return ImmutableList.copyOf(result);
        } catch (ClientProtocolException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (AuthenticationException e) {
            throw new RuntimeException(e);
        } finally {
            if (response != null) {
                try {
                    response.getEntity().getContent().close();
                } catch (IllegalStateException e) {
                    System.err.println(getClass().getName() + ": " + e.getMessage());
                } catch (IOException e) {
                    System.err.println(getClass().getName() + ": " + e.getMessage());
                }
            }
        }
    }

    private String previewOf(IssueResult input) {
        JsonElement el = input.fields.get("attachment");
        if (el == null) {
            return null;
        }
        el = el.getAsJsonObject().get("value");
        if (el == null) {
            return null;
        }
        if (!el.isJsonArray()) {
            return null;
        }
        JsonArray arr = el.getAsJsonArray();
        for (int i = 0 ; i < arr.size() ; i++) {
            JsonObject obj = arr.get(i).getAsJsonObject();
            JsonPrimitive file = obj.getAsJsonPrimitive("filename");
            JsonPrimitive mime = obj.getAsJsonPrimitive("mimeType");
            JsonPrimitive content = obj.getAsJsonPrimitive("content");
            if (log.isDebugEnabled()) {
                log.debug("Attachment " + file + " of type " + mime + " at " + content);
            }
            if (file == null || !file.getAsString().matches(".*preview.*")) {
                log.debug("Not a preview file");
                continue;
            }
            if (mime != null) {
                if (!mime.getAsString().startsWith("image/")) {
                    log.debug("MediaType not an image");
                    continue;
                }
            } else {
                if (!file.getAsString().matches(".*jpg|jpeg|png")) {
                    log.debug("Filename not an image jpg|jpeg|png");
                    continue;
                }
            }
            if (content != null) {
                return content.getAsString();
            }
        }
        return null;
    }

    private String dateOf(IssueResult input) {
        JsonElement el = input.fields.get("resolutiondate");
        if (el == null) {
            return null;
        }
        el = el.getAsJsonObject().get("value");
        if (el == null) {
            return null;
        }
        String date = el.getAsString();
        int index = date.indexOf('T');
        if (index == -1) {
            return date;
        }
        return date.substring(0, index);
    }

    boolean filter(IssueResult input) {
        for (Map.Entry<String, String> exclude : excludes.entrySet()) {
            JsonElement element = input.fields.get(exclude.getKey());
            if (element == null) {
                continue;
            }
            element = element.getAsJsonObject().get("value");
            if (element == null) {
                continue;
            }
            if (element.isJsonPrimitive() && exclude.getValue().equals(element.getAsJsonPrimitive().getAsString())) {
                return false;
            }
            if (element.isJsonArray()) {
                JsonArray array = element.getAsJsonArray();
                for (int i = 0 ; i < array.size() ; i++) {
                    if (exclude.getValue().equals(array.get(i).getAsJsonPrimitive().getAsString())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    String describe(IssueResult details) {
        for (String field : fields) {
            JsonElement value = details.fields.get(field);
            if (value != null && value.isJsonObject()) {
                JsonObject obj = value.getAsJsonObject();
                if (obj.has("value") && obj.get("value").isJsonPrimitive()) {
                    return obj.getAsJsonPrimitive("value").getAsString();
                }
            }
        }
        throw new RuntimeException(details.key + " has no notes (tried " + fields + ")");
    }

    IssueResult getIssue(String key)
    {
        HttpGet item = new HttpGet(url + "/issue/" + key);
        if (log.isDebugEnabled()) {
            log.debug("ISSUE " + item.getURI().toASCIIString());
        }
        HttpResponse response = null;
        try {
            item.addHeader(scheme.authenticate(creds, item, ctx));
            response = client.execute(item);
            InputStream stream = response.getEntity().getContent();
            if (log.isDebugEnabled()) {
                ByteArrayOutputStream copy = new ByteArrayOutputStream();
                IOUtil.copy(stream, copy);
                byte[] bytes = copy.toByteArray();
                log.debug("ISSUE RESPONSE " + new String(bytes, "UTF-8"));
                stream = new ByteArrayInputStream(bytes);
            }
            return gson.fromJson(new InputStreamReader(stream, "UTF-8"), IssueResult.class);
        } catch (AuthenticationException e) {
            throw new RuntimeException(e);
        } catch (ClientProtocolException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (response != null) {
                try {
                    response.getEntity().getContent().close();
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void downloadImages(Iterable<Change> changes, File outputDirectory)
    {
        for (Change change : changes) {
            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs();
            }
            HttpGet request = new HttpGet(change.previewUrl);
            try {
                request.addHeader(scheme.authenticate(creds, request, ctx));
                CloseableHttpResponse response = client.execute(request);
                FileOutputStream fos = new FileOutputStream(new File(outputDirectory, change.preview));
                IOUtil.copy(response.getEntity().getContent(), fos);
                fos.close();
            } catch (AuthenticationException e) {
                throw new RuntimeException(e);
            } catch (ClientProtocolException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private String suffix(String preview) {
        int index = preview.lastIndexOf('.');
        if (index == -1) {
            return "jpeg";
        }
        return preview.substring(index+1);
    }
}
