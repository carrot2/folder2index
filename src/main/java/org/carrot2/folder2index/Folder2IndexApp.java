package org.carrot2.folder2index;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.util.Version;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.google.common.io.Closeables;
import com.google.common.io.Files;

public class Folder2IndexApp
{
    @Option(name = "--index", metaVar = "DIR", required = true, usage = "Where to create the index")
    File index;

    @Option(name = "--folders", metaVar = "DIR", required = true, usage = "Folders or files to index")
    List<File> folders;

    @Option(name = "--encoding", metaVar = "CHARSET", required = false, usage = "Encoding to assume for text files")
    String encoding = "UTF-8";

    public int process()
    {
        IndexWriter writer = null;
        try
        {
            writer = new IndexWriter(FSDirectory.open(index), new IndexWriterConfig(
                Version.LUCENE_31, new StandardAnalyzer(Version.LUCENE_31)));
            index(folders, writer);
        }
        catch (CorruptIndexException e)
        {
            throw new RuntimeException(e);
        }
        catch (LockObtainFailedException e)
        {
            throw new RuntimeException(e);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            Closeables.closeQuietly(writer);
        }
        System.out.println("Index created: " + index.getAbsolutePath());
        return 0;
    }

    private void index(List<File> files, IndexWriter writer) throws IOException
    {
        for (File file : files)
        {
            if (file.exists() && file.canRead())
            {
                if (file.isDirectory())
                {
                    index(Arrays.asList(file.listFiles()), writer);
                }
                else
                {
                    index(file, writer);
                }
            }
            else
            {
                System.out.println("Skipping non-existing or unreadable file: "
                    + file.getAbsolutePath());
            }
        }
    }

    private void index(File file, IndexWriter writer) throws IOException
    {
        if (file.getName().endsWith(".txt"))
        {
            System.out.println("Indexing: " + file.getAbsolutePath());
            final String content = Files.toString(file, Charset.forName(encoding));
            final Document doc = new Document();
            doc.add(new Field("content", content, Field.Store.YES, Field.Index.ANALYZED));
            doc.add(new Field("fileName", file.getName(), Field.Store.YES,
                Field.Index.NOT_ANALYZED));
            doc.add(new Field("filePath", file.getAbsolutePath(), Field.Store.YES,
                Field.Index.NOT_ANALYZED));
            writer.addDocument(doc);
        }
        else
        {
            System.out.println("Skipping non-text file: " + file.getAbsolutePath());
        }
    }

    public static void main(String [] args)
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
            System.out.print("Usage: folder2index");
            parser.printSingleLineUsage(System.out);
            System.out.println();
            parser.printUsage(System.out);
        }
    }
}
