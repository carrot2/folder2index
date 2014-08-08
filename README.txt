
folder2index
------------

Converts PDF, TXT or HTML documents to a Lucene index (for use with Carrot2 Clustering Workbench)

Quick usage guide
-----------------

- install Apache Maven.

- run:
  mvn clean package
  
- cd target

- prepare a folder FOO with your PDF, HTML or plain text files. Prepare an empty folder BAR
  for the index.

- run:

  java -jar folder2index-0.0.2.jar --folder FOO --index BAR --use-tika

The index will be created. Download and open Carrot2 Workbench.

http://project.carrot2.org/download.html

Select Lucene as the document source and pick the correct fields for the title, content and URL (pick file path as
the URL field).

http://download.carrot2.org/head/manual/index.html#section.getting-started.lucene

Select other input options (how many results to cluster, query or *:*) and run your clustering.
