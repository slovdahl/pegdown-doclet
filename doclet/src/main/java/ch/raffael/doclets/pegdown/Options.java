/*
 * Copyright 2013 Raffael Herzog
 *
 * This file is part of pegdown-doclet.
 *
 * pegdown-doclet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * pegdown-doclet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with pegdown-doclet.  If not, see <http://www.gnu.org/licenses/>.
 */
package ch.raffael.doclets.pegdown;

import java.io.File;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.common.base.Splitter;
import com.google.common.collect.Ordering;
import com.sun.javadoc.DocErrorReporter;
import com.sun.tools.doclets.standard.Standard;
import org.pegdown.Extensions;
import org.pegdown.LinkRenderer;
import org.pegdown.PegDownProcessor;
import org.pegdown.ToHtmlSerializer;

import static com.google.common.base.MoreObjects.*;


/**
 * Processes and stores the command line options.
 *
 * @author <p><a href="mailto:herzog@raffael.ch">Raffael Herzog</a></p>
 */
public class Options {

    public static final String OPT_EXTENSIONS = "-extensions";
    public static final String OPT_DISABLE_HIGHLIGHT = "-disable-highlight";
    public static final String OPT_ENABLE_AUTO_HIGHLIGHT = "-enable-auto-highlight";
    public static final String OPT_HIGHLIGHT_STYLE = "-highlight-style";
    public static final String OPT_PLANTUML_CONFIG = "-plantuml-config";
    public static final String OPT_PARSE_TIMEOUT = "-parse-timeout";
    public static final String OPT_ENCODING = "-encoding";
    public static final String OPT_OVERVIEW = "-overview";
    public static final String OPT_OUTPUT_DIR = "-d";
    public static final String OPT_STYLESHEETFILE = "-stylesheetfile";
    public static final String OPT_JAVADOCVERSION = "-javadocversion";
    public static final String OPT_TODO_TITLE = "-todo-title";

    private static final Pattern LINE_START = Pattern.compile("^ ", Pattern.MULTILINE);
    private static final Pattern MARKERS = Pattern.compile("\\020[et]");

    /**
     * The default extensions for Pegdown. This includes the following extensions:
     *
     * * {@link Extensions#AUTOLINKS}
     * * {@link Extensions#DEFINITIONS}
     * * {@link Extensions#FENCED_CODE_BLOCKS}
     * * {@link Extensions#SMARTYPANTS}
     * * {@link Extensions#TABLES}
     * * {@link Extensions#WIKILINKS}
     */
    public static final int DEFAULT_PEGDOWN_EXTENSIONS =
            Extensions.AUTOLINKS
            | Extensions.DEFINITIONS
            | Extensions.FENCED_CODE_BLOCKS
            | Extensions.SMARTYPANTS
            | Extensions.TABLES
            | Extensions.WIKILINKS;

    private String[][] forwardedOptions = new String[0][];

    private Integer pegdownExtensions = null;
    private File overviewFile = null;
    private Charset encoding = null;
    private File destinationDir = null;
    private File stylesheetFile = null;
    private JavadocQuirks javadocVersion = null;
    private File plantUmlConfigFile = null;
    private boolean highlightEnabled = true;
    private boolean autoHighlightEnabled = false;
    private String highlightStyle = null;
    private Long parseTimeout;
    private String todoTitle = null;

    private LinkRenderer linkRenderer = null;
    private PegDownProcessor processor = null;

    public Options() {
    }

    /**
     * Retrieves the options to be forwarded to the standard Doclet.
     *
     * @return The options for the standard Doclet.
     */
    public String[][] forwardedOptions() {
        return forwardedOptions;
    }

    /**
     * Loads the options from the command line.
     *
     * @param options          The command line options.
     * @param errorReporter    The error reporter for printing messages.
     *
     * @return The options to be forwarded to the standard doclet.
     */
    public String[][] load(String[][] options, DocErrorReporter errorReporter) {
        LinkedList<String[]> optionsList = new LinkedList<>(Arrays.asList(options));
        Iterator<String[]> optionsIter = optionsList.iterator();
        while ( optionsIter.hasNext() ) {
            String[] opt = optionsIter.next();
            if ( !handleOption(optionsIter, opt, errorReporter) ) {
                return null;
            }
        }
        if ( pegdownExtensions == null ) {
            setPegdownExtensions(DEFAULT_PEGDOWN_EXTENSIONS);
        }
        return optionsList.toArray(new String[optionsList.size()][]);
    }

    protected boolean handleOption(Iterator<String[]> optionsIter, String[] opt, DocErrorReporter errorReporter) {
        if ( opt[0].equals(OPT_EXTENSIONS) ) {
            if ( pegdownExtensions != null ) {
                errorReporter.printError("Only one " + OPT_EXTENSIONS + " option allowed");
                return false;
            }
            try {
                setPegdownExtensions(toExtensions(opt[1]));
            }
            catch ( IllegalArgumentException e ) {
                errorReporter.printError(e.getMessage());
                return false;
            }
            optionsIter.remove();
        }
        else if ( opt[0].equals(OPT_DISABLE_HIGHLIGHT) ) {
            highlightEnabled = false;
            optionsIter.remove();
        }
        else if ( opt[0].equals(OPT_ENABLE_AUTO_HIGHLIGHT) ) {
            autoHighlightEnabled = true;
            optionsIter.remove();
        }
        else if ( opt[0].equals(OPT_HIGHLIGHT_STYLE) ) {
            if ( highlightStyle != null ) {
                errorReporter.printError("Only one " + OPT_HIGHLIGHT_STYLE + " option allowed");
                return false;
            }
            highlightStyle = opt[1];
            optionsIter.remove();
        }
        else if ( opt[0].equals(OPT_PLANTUML_CONFIG) ) {
            if ( plantUmlConfigFile != null ) {
                errorReporter.printError("Only one " + OPT_PLANTUML_CONFIG + " option allowed");
                return false;
            }
            setPlantUmlConfigFile(new File(opt[1]));
            optionsIter.remove();
        }
        else if ( opt[0].equals(OPT_PARSE_TIMEOUT) ) {
            if ( parseTimeout != null ) {
                errorReporter.printError("Only one -parse-timeout option allowed");
                return false;
            }
            BigDecimal millis = new BigDecimal(opt[1]).movePointRight(3);
            if ( millis.compareTo(BigDecimal.ZERO) <= 0 || millis.compareTo(new BigDecimal(Long.MAX_VALUE)) > 0 ) {
                errorReporter.printError("Invalid timeout value: " + opt[1]);
                return false;
            }
            parseTimeout = millis.longValue();
            optionsIter.remove();
        }
        else if ( opt[0].equals(OPT_ENCODING) ) {
            try {
                encoding = Charset.forName(opt[1]);
            }
            catch ( IllegalCharsetNameException e ) {
                errorReporter.printError("Illegal charset: " + opt[1]);
                return false;
            }
        }
        else if ( opt[0].equals(OPT_OVERVIEW) ) {
            if ( getOverviewFile() != null ) {
                errorReporter.printError(OPT_OVERVIEW + " may only be specified once");
                return false;
            }
            setOverviewFile(new File(opt[1]));
            optionsIter.remove();
        }
        else if ( opt[0].equals(OPT_OUTPUT_DIR) ) {
            if ( destinationDir != null ) {
                errorReporter.printError(OPT_OUTPUT_DIR + " may only be specified once");
            }
            setDestinationDir(new File(opt[1]));
        }
        else if ( opt[0].equals(OPT_STYLESHEETFILE) ) {
            if ( stylesheetFile != null ) {
                errorReporter.printError(OPT_STYLESHEETFILE + " may only specified once");
            }
            setStylesheetFile(new File(opt[1]));
        }
        else if ( opt[0].equals(OPT_JAVADOCVERSION) ) {
            if ( javadocVersion != null ) {
                errorReporter.printError(OPT_JAVADOCVERSION + " may only specified once");
            }
            try {
                setJavadocVersion(JavadocQuirks.valueOf(opt[1].toUpperCase()));
            }
            catch ( IllegalArgumentException e ) {
                errorReporter.printError("Unknown value " + opt[1] + " for " + OPT_JAVADOCVERSION);
            }
            optionsIter.remove();
        }
        else if ( opt[0].equals(OPT_TODO_TITLE) ) {
            if ( todoTitle != null ) {
                errorReporter.printError(OPT_TODO_TITLE + " may only specified once");
            }
            setTodoTitle(todoTitle);
            optionsIter.remove();
        }
        return true;
    }

    /**
     * Gets the Pegdown extension flags.
     *
     * @return The Pegdown extension flags.
     *
     * @see Extensions
     */
    public int getPegdownExtensions() {
        return pegdownExtensions != null ? pegdownExtensions : DEFAULT_PEGDOWN_EXTENSIONS;
    }

    /**
     * Sets the Pegdown extension flags.
     *
     * @param pegdownExtensions    The Pegdown extension flags.
     *
     * @see Extensions
     */
    public void setPegdownExtensions(int pegdownExtensions) {
        this.pegdownExtensions = pegdownExtensions;
        processor = null;
    }

    /**
     * Gets the overview file.
     *
     * @return The overview file.
     */
    public File getOverviewFile() {
        return overviewFile;
    }

    /**
     * Sets the overview file.
     *
     * @param overviewFile The overview file.
     */
    public void setOverviewFile(File overviewFile) {
        this.overviewFile = overviewFile;
    }

    /**
     * Gets the source encoding.
     *
     * @return The source encoding.
     */
    public Charset getEncoding() {
        return firstNonNull(encoding, Charset.defaultCharset());
    }

    /**
     * Sets the source encoding.
     *
     * @param encoding The source encoding.
     */
    public void setEncoding(Charset encoding) {
        this.encoding = encoding;
    }

    /**
     * Gets the destination directory.
     *
     * @return The destination directory.
     */
    public File getDestinationDir() {
        if ( destinationDir == null ) {
            destinationDir = new File(System.getProperty("user.dir"));
        }
        return destinationDir;
    }

    /**
     * Sets the destination directory.
     *
     * @param destinationDir    The destination directory
     */
    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    /**
     * Gets the CSS stylesheet file.
     *
     * @return The stylesheet file.
     */
    public File getStylesheetFile() {
        return stylesheetFile;
    }

    /**
     * Sets the CSS stylesheet file.
     *
     * @param stylesheetFile The stylesheet file.
     */
    public void setStylesheetFile(File stylesheetFile) {
        this.stylesheetFile = stylesheetFile;
    }

    /**
     * Gets the Javadoc version.
     *
     * @return The Javadoc version.
     */
    public JavadocQuirks getJavadocVersion() {
        if ( javadocVersion == null ) {
            String javaVersion = System.getProperty("java.version");
            if ( javaVersion != null && javaVersion.compareTo("1.8") >= 0 ) {
                return JavadocQuirks.V8;
            }
            else {
                return JavadocQuirks.V7;
            }
        }
        return javadocVersion;
    }

    /**
     * Sets the Javadoc version.
     *
     * @param javadocVersion    The Javadoc version or `null` to determine the version automatically.
     */
    public void setJavadocVersion(JavadocQuirks javadocVersion) {
        this.javadocVersion = javadocVersion;
    }

    /**
     * Gets the PlantUML configuration file.
     *
     * @return The PlantUML configuration file.
     */
    public File getPlantUmlConfigFile() {
        return plantUmlConfigFile;
    }

    /**
     * Sets the PlantUML configuration file.
     *
     * @param plantUmlConfigFile    The PlantUML configuration file.
     */
    public void setPlantUmlConfigFile(File plantUmlConfigFile) {
        this.plantUmlConfigFile = plantUmlConfigFile;
    }

    /**
     * Gets the link renderer.
     *
     * @return The link renderer.
     */
    public LinkRenderer getLinkRenderer() {
        if ( linkRenderer == null ) {
            linkRenderer = new DocletLinkRenderer();
        }
        return linkRenderer;
    }

    /**
     * Sets the link renderer.
     *
     * @param linkRenderer The link renderer.
     */
    public void setLinkRenderer(LinkRenderer linkRenderer) {
        this.linkRenderer = linkRenderer;
    }

    public boolean isHighlightEnabled() {
        return highlightEnabled;
    }

    public void setHighlightEnabled(boolean highlightEnabled) {
        this.highlightEnabled = highlightEnabled;
    }

    public boolean isAutoHighlightEnabled() {
        return autoHighlightEnabled;
    }

    public void setAutoHighlightEnabled(boolean autoHighlightEnabled) {
        this.autoHighlightEnabled = autoHighlightEnabled;
    }

    public String getHighlightStyle() {
        return firstNonNull(highlightStyle, "default");
    }

    public void setHighlightStyle(String highlightStyle) {
        this.highlightStyle = highlightStyle;
    }

    public String getTodoTitle() {
        return firstNonNull(todoTitle, "To Do");
    }

    public void setTodoTitle(String todoTitle) {
        this.todoTitle = todoTitle;
    }

    /**
     * Gets the parse timeout in milliseconds for the Pegdown processor.
     *
     * @return The parse timeout.
     *
     * @see PegDownProcessor#PegDownProcessor(int, long)
     */
    public long getParseTimeout() {
        processor = null;
        return parseTimeout != null ? parseTimeout : PegDownProcessor.DEFAULT_MAX_PARSING_TIME;
    }

    /**
     * Sets the parse timeout in milliseconds for the Pegdown processor.
     *
     * @param parseTimeout    The new parse timeout.
     *
     * @see PegDownProcessor#PegDownProcessor(int, long)
     */
    public void setParseTimeout(long parseTimeout) {
        this.parseTimeout = parseTimeout;
    }

    /**
     * Converts Markdown source to HTML according to this options object. Leading spaces
     * will be fixed.
     *
     * @param markup    The Markdown source.
     *
     * @return The resulting HTML.
     *
     * @see #toHtml(String, boolean)
     */
    public String toHtml(String markup) {
        return toHtml(markup, true);
    }

    /**
     * Converts Markdown source to HTML according to this options object. If
     * `fixLeadingSpaces` is `true`, exactly one leading whitespace character ('\\u0020')
     * will be removed, if it exists.
     *
     * @todo This method doesn't belong here, move it to {@link PegdownDoclet}.
     *
     * @param markup           The Markdown source.
     * @param fixLeadingSpaces `true` if leading spaces should be fixed.
     *
     * @return The resulting HTML.
     */
    public String toHtml(String markup, boolean fixLeadingSpaces) {
        if ( processor == null ) {
            processor = createProcessor();
        }
        if ( fixLeadingSpaces ) {
            markup = LINE_START.matcher(markup).replaceAll("");
        }
        List<String> tags = new ArrayList<>();
        String html = createDocletSerializer().toHtml(processor.parseMarkdown(Tags.extractInlineTags(markup, tags).toCharArray()));
        return Tags.insertInlineTags(html, tags);
    }

    /**
     * Create a new processor. If you need to further customise the markup processing,
     * you can override this method.
     *
     * @return A (possibly customised) Pegdown processor.
     */
    protected PegDownProcessor createProcessor() {
        return new PegDownProcessor(firstNonNull(pegdownExtensions, DEFAULT_PEGDOWN_EXTENSIONS), getParseTimeout());
    }

    /**
     * Create a new serializer. If you need to further customize the HTML rendering, you
     * can override this method.
     *
     * @return A (possibly customised) ToHtmlSerializer.
     */
    protected ToHtmlSerializer createDocletSerializer() {
        return new DocletSerializer(this, getLinkRenderer());
    }

    public static int optionLength(String option) {
        switch ( option ) {
            case OPT_EXTENSIONS:
            case OPT_PLANTUML_CONFIG:
            case OPT_HIGHLIGHT_STYLE:
            case OPT_PARSE_TIMEOUT:
            case OPT_TODO_TITLE:
            case OPT_JAVADOCVERSION:
                return 2;
            case OPT_DISABLE_HIGHLIGHT:
            case OPT_ENABLE_AUTO_HIGHLIGHT:
                return 1;
            default:
                return Standard.optionLength(option);
        }
    }

    /**
     * As specified by the Doclet specification.
     *
     * @param options          The command line options.
     * @param errorReporter    An error reporter to print messages.
     *
     * @return `true` if the options are valid.
     *
     * @see com.sun.javadoc.Doclet#validOptions(String[][], com.sun.javadoc.DocErrorReporter)
     */
    public static boolean validOptions(String[][] options, DocErrorReporter errorReporter) {
        options = new Options().load(options, errorReporter);
        if ( options != null ) {
            return Standard.validOptions(options, errorReporter);
        }
        else {
            return false;
        }
    }

    /**
     * Convert a comma separated list of extension names to an int. Each name is
     * converted to upper case, any '-' is replaced by '_'. The result is expected to
     * be a flag from {@link Extensions}.
     *
     * @param extensions    A comma separated list of PegDown extensions.
     *
     * @return An `int` representing the enabled Pegdown extensions.
     */
    public static int toExtensions(String extensions) {
        int result = 0;
        for ( String ext : Splitter.on(',').trimResults().omitEmptyStrings().split(extensions) ) {
            try {
                Field f = Extensions.class.getField(ext.replace('-', '_').toUpperCase());
                result |= (int)f.get(null);
            }
            catch ( NoSuchFieldException e ) {
                throw new IllegalArgumentException("No such extension: " + ext);
            }
            catch ( IllegalAccessException e ) {
                throw new IllegalArgumentException("Cannot read int value for extension " + ext + ": " + e, e);
            }
        }
        return result;
    }

    public enum JavadocQuirks {
        V7("stylesheet.7.css"), V8("stylesheet.8.css");

        private final String stylesheet;

        JavadocQuirks(String stylesheet) {
            this.stylesheet = stylesheet;
        }

        public String getStylesheet() {
            return stylesheet;
        }
    }

}
