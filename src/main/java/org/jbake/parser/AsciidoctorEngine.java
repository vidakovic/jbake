package org.jbake.parser;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.Configuration;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.DocumentHeader;
import org.asciidoctor.Options;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.apache.commons.lang.BooleanUtils.toBooleanObject;
import static org.apache.commons.lang.math.NumberUtils.isNumber;
import static org.apache.commons.lang.math.NumberUtils.toInt;
import static org.asciidoctor.AttributesBuilder.attributes;
import static org.asciidoctor.OptionsBuilder.options;
import static org.asciidoctor.SafeMode.UNSAFE;

/**
 * Renders documents in the asciidoc format using the Asciidoctor engine.
 *
 * @author Cédric Champeau
 */
public class AsciidoctorEngine extends MarkupEngine {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private Asciidoctor engine;

    private Asciidoctor getEngine() {
        try {
            lock.readLock().lock();
            if (engine==null) {
                lock.readLock().unlock();
                try {
                    lock.writeLock().lock();
                    if (engine==null) {
                        System.out.print("Initializing Asciidoctor engine...");
                        engine = Asciidoctor.Factory.create();
                        System.out.println(" ok");
                    }
                } finally {
                    lock.readLock().lock();
                    lock.writeLock().unlock();
                }
            }
        } finally {
            lock.readLock().unlock();
        }
        return engine;
    }

    @Override
    public void processHeader(final ParserContext context) {
        final Asciidoctor asciidoctor = getEngine();
        DocumentHeader header = asciidoctor.readDocumentHeader(context.getFile());
        Map<String, Object> contents = context.getContents();
        if (header.getDocumentTitle() != null) {
            contents.put("title", header.getDocumentTitle());
        }
        Map<String, Object> attributes = header.getAttributes();
        for (String key : attributes.keySet()) {
            if (key.startsWith("jbake-")) {
                Object val = attributes.get(key);
                if (val!=null) {
                    String pKey = key.substring(6);
                    contents.put(pKey, val);
                }
            }
            if (key.equals("revdate")) {
                if (attributes.get(key) != null && attributes.get(key) instanceof String) {

                    DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
                    Date date = null;
                    try {
                        date = df.parse((String)attributes.get(key));
                        contents.put("date", date);
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
            }
            if (key.equals("jbake-tags")) {
                if (attributes.get(key) != null && attributes.get(key) instanceof String) {
                    contents.put("tags", ((String) attributes.get(key)).split(","));
                }
            } else {
                contents.put(key, attributes.get(key));
            }
        }
    }

    @Override
    public void processBody(ParserContext context) {
        StringBuilder body = new StringBuilder(context.getBody().length());
        if (!context.hasHeader()) {
            for (String line : context.getFileLines()) {
                body.append(line).append("\n");
            }
            context.setBody(body.toString());
        }
        processAsciiDoc(context);
    }

    private void processAsciiDoc(ParserContext context) {
        final Asciidoctor asciidoctor = getEngine();
        Options options = getAsciiDocOptionsAndAttributes(context);
        context.setBody(asciidoctor.render(context.getBody(), options));
    }

    private Options getAsciiDocOptionsAndAttributes(ParserContext context) {
        CompositeConfiguration config = context.getConfig();
        Attributes attributes = attributes(config.getStringArray("asciidoctor.attributes")).get();
        Configuration optionsSubset = config.subset("asciidoctor.option");
        Options options = options().attributes(attributes).get();
        for (Iterator<String> iterator = optionsSubset.getKeys(); iterator.hasNext();) {
            String name = iterator.next();
            options.setOption(name, guessTypeByContent(optionsSubset.getString(name)));
        }
        options.setBaseDir(context.getContentPath());
        options.setSafe(UNSAFE);
        return options;
    }

    /**
     * Guess the type by content it has.
     * @param value
     * @return boolean,integer of string as fallback
     */
    private static Object guessTypeByContent(String value){
        if (toBooleanObject(value)!=null)
            return toBooleanObject(value);
        if(isNumber(value))
            return toInt(value);
        return value;
    }
}