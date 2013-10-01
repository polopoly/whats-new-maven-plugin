package com.atex;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

public class WhatsNewTemplate {
    private File target;
    private File template;
    private List<String> changes;

    public WhatsNewTemplate(File target, File template, List<String> changes) {
        this.target = target;
        this.template = template;
        this.changes = changes;
    }

    public void write() {
        if (!template.exists()) {
            throw new RuntimeException("Template file " + template + " does not exist");
        }
        if (!target.exists()) {
            target.mkdirs();
        }
        Properties props = new Properties();
        props.setProperty("file.resource.loader.path", template.getParent());
        VelocityEngine engine = new VelocityEngine(props);
        engine.init();
        Template tmpl = engine.getTemplate(template.getName());
        File targetFile = new File(target, "whatsnew.html");
        try {
            FileWriter fw = new FileWriter(targetFile);
            VelocityContext context = new VelocityContext();
            context.put("changes", changes);
            tmpl.merge(context, fw);
            fw.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
