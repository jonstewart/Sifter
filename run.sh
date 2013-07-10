export HADOOP_OPTS="-Djava.security.krb5.realm= -Djava.security.krb5.kdc="
java -cp lib/*:sifter.jar -Xmx1g $HADOOP_OPTS edu.utsa.sifter.$1 $2
