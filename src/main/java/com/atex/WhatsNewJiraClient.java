package com.atex;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
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

public class WhatsNewJiraClient {

    private String url;
    private String user;
    private String pass;

    public String project = "ART";
    public String version = "2.0.0";
    public ImmutableList<String> fields = ImmutableList.of("summary");
    public ImmutableMap<String, String> excludes = ImmutableMap.of();
    public Log log;

    public WhatsNewJiraClient(String url, String user, String pass) {
        this.url = url;
        this.user = user;
        this.pass = pass;
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

    public static class Change {
        public String date;
        public String change;
        public String getDate() { return date; }
        public String getChange() { return change; }
    }

    public List<Change> changes() {
        HttpGet get;
        try {
            URIBuilder builder = new URIBuilder(url + "/search");
            builder.addParameter("jql", String.format("project = '%s' and fixVersion = '%s' and status in ('Closed', 'Resolved') order by resolutiondate desc", project, version));
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
            final HttpClient client = HttpClientBuilder.create().build();
            final Credentials creds = new UsernamePasswordCredentials(user, pass);
            final HttpContext ctx = new BasicHttpContext();
            final BasicScheme scheme = new BasicScheme();
            final Gson gson = new GsonBuilder().create();
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
            Iterable<IssueResult> issues = Iterables.transform(res.issues, new Function<SearchResultIssue, IssueResult>() {
                public IssueResult apply(SearchResultIssue input) {
                    return getIssue(input.key, scheme, creds, ctx, client, gson);
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
                change.date = dateOf(ir);
                change.change = describe(ir);
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

    IssueResult getIssue(String key, BasicScheme scheme, Credentials creds, HttpContext ctx, HttpClient client, Gson gson)
    {
        HttpGet item = new HttpGet(url + "/issue/" + key);
        if (log != null) {
            log.debug("ISSUE " + item.getURI().toASCIIString());
        }
        HttpResponse response = null;
        try {
            item.addHeader(scheme.authenticate(creds, item, ctx));
            response = client.execute(item);
            InputStream stream = response.getEntity().getContent();
            if (log != null) {
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
}
