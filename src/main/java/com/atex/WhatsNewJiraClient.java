package com.atex;

import java.io.IOException;
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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class WhatsNewJiraClient {

    private String url;
    private String user;
    private String pass;

    public String project = "ART";
    public String version = "2.0.0";
    public ImmutableList<String> fields = ImmutableList.of("summary"); 

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

    public List<String> changes() {
        HttpGet get;
        try {
            URIBuilder builder = new URIBuilder(url + "/search");
            builder.addParameter("jql", String.format("project = '%s' and fixVersion = '%s' and status in ('Closed', 'Resolved')", project, version));
            builder.addParameter("maxResults", "100");
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
            SearchResult res = gson.fromJson(new InputStreamReader(response.getEntity().getContent(), "UTF-8"), SearchResult.class);
            return Lists.transform(res.issues, new Function<SearchResultIssue, String>() {
                public String apply(SearchResultIssue item) {
                    IssueResult details = getIssue(item.key, scheme, creds, ctx, client, gson);
                    return describe(details);
                }
            });
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
        HttpResponse response = null;
        try {
            item.addHeader(scheme.authenticate(creds, item, ctx));
            response = client.execute(item);
            return gson.fromJson(new InputStreamReader(response.getEntity().getContent(), "UTF-8"), IssueResult.class);
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
