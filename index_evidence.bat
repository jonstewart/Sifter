echo "2 args: [path to evidence file] [stoplist to use]"
echo "Pipe in dumpfiles output from fsrip.exe"

java -cp sifter.jar;lib\* -Xms512m -Xmx4g -XX:+UseConcMarkSweepGC edu.utsa.sifter.Indexer %1 %2
