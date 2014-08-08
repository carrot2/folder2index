package org.carrot2.folder2index;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashSet;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.parser.txt.TXTParser;
import org.apache.tika.sax.BodyContentHandler;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.xml.sax.SAXException;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

public class Folder2IndexApp
{
    @Option(name = "--index", metaVar = "DIR", required = true, usage = "Target Lucene index folder")
    File index;

    @Option(name = "--folder", metaVar = "DIR", required = true, usage = "Folder or file to index")
    File folder;

    @Option(name = "--encoding", metaVar = "CHARSET", required = false, usage = "Character encoding for .txt files")
    String encoding = "UTF-8";
    
    @Option(name = "--use-tika", required = false, usage = "Use Apache Tika to parse PDF, HTML and TXT files.")
    boolean useTika;

    public int process() throws IOException
    {
        Version version = Version.LUCENE_46;
        try (IndexWriter writer = new IndexWriter(
                FSDirectory.open(index), 
                new IndexWriterConfig(version, new StandardAnalyzer(version)))) {
            index(new ArrayDeque<>(Arrays.asList(folder)), writer);
        }
        System.out.println("Index created: " + index.getAbsolutePath());
        return 0;
    }

    private void index(Deque<File> filesOrDirs, IndexWriter writer) throws IOException
    {
        LinkedHashSet<File> files = Sets.newLinkedHashSet();
        while (!filesOrDirs.isEmpty())
        {
            File f = filesOrDirs.pop();
            if (f.isFile())
            {
                if (f.canRead() && f.exists()) {
                    files.add(f);
                } else {
                    System.out.println("Skipping non-existent or unreadable file: " + f.getAbsolutePath());
                }
            }
            else if (f.isDirectory())
            {
                filesOrDirs.addAll(Arrays.asList(f.listFiles()));
            }
        }

        if (useTika)
        {
            AutoDetectParser parser = new AutoDetectParser(
                new PDFParser(),
                new TXTParser(),
                new HtmlParser());

            for (File f : files)
            {
                try (InputStream is = Files.asByteSource(f).openBufferedStream())
                {
                    StringWriter w = new StringWriter();
                    BodyContentHandler handler = new BodyContentHandler(w);
                    Metadata metadata = new Metadata();
                    parser.parse(is, handler, metadata);

                    String contentType = Objects.firstNonNull(metadata.get(Metadata.CONTENT_TYPE), "<unknown>");
                    String title = metadata.get(TikaCoreProperties.TITLE);
                    String body  = w.toString();

                    System.out.println("Parsed: " + f.getName());
                    System.out.println("  > Content-type: " + contentType);
                    System.out.println("  > Content-encoding: " + Objects.firstNonNull(metadata.get(Metadata.CONTENT_ENCODING), "<unknown>"));
                    System.out.println("  > Title: " + Objects.firstNonNull(title, "").length() + " characters.");
                    System.out.println("  > Content: " + Objects.firstNonNull(body, "").length() + " characters.");
                    
                    final Document doc = new Document();
                    if (!Strings.isNullOrEmpty(title)) doc.add(new TextField("title", title, Store.YES));
                    if (!Strings.isNullOrEmpty(body)) doc.add(new TextField("content", body, Store.YES));
                    doc.add(new StringField("fileName", f.getName(), Store.YES));
                    doc.add(new StringField("filePath", f.getAbsolutePath(), Store.YES));
                    doc.add(new StringField("contentType", contentType, Store.YES));
                    writer.addDocument(doc);
                }
                catch (SAXException | TikaException e)
                {
                    System.out.println("Couldn't parse " + f.getAbsolutePath() + ": " + e.toString());
                }
            }
        }
        else
        {
            for (File f : files)
            {
                indexPlainText(f, writer);
            }
        }
    }

    private void indexPlainText(File file, IndexWriter writer) throws IOException
    {
        if (file.getName().endsWith(".txt"))
        {
            System.out.println("Indexing: " + file.getAbsolutePath());

            final String content = Files.toString(file, Charset.forName(encoding));
            final Document doc = new Document();
            doc.add(new TextField("content", content, Store.YES));
            doc.add(new StringField("fileName", file.getName(), Store.YES));
            doc.add(new StringField("filePath", file.getAbsolutePath(), Store.YES));
            writer.addDocument(doc);
        }
        else
        {
            System.out.println("Skipping non-text file: " + file.getAbsolutePath());
        }
    }

    public static void main(String [] args) throws Exception
    {
        final Folder2IndexApp app = new Folder2IndexApp();

        final CmdLineParser parser = new CmdLineParser(app);
        parser.setUsageWidth(80);

        try
        {
            parser.parseArgument(args);
            System.exit(app.process());
        }
        catch (CmdLineException e)
        {
            System.out.println("Wrong arguments: " + e);
            
            System.out.print("Usage: folder2index");
            parser.printSingleLineUsage(System.out);
            System.out.println();
            parser.printUsage(System.out);
        }
    }
}
