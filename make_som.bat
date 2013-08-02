echo "Pass path to evidence index directory"
java -cp sifter.jar;lib\* -Xmx1g edu.utsa.sifter.som.MainSOM %1 client
