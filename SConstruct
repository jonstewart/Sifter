import sys
import os
import glob

env = Environment()

jars = glob.glob('lib/*.jar')

env.Append(JAVACLASSPATH=':'.join(jars))

print("Classpath = " + str(env['JAVACLASSPATH']))

env.Java('build', 'src')
#manifest = env.Command('build/META-INF/MANIFEST.MF', 'src/WEB-INF/classes/MANIFEST.MF', Copy('$TARGET', '$SOURCE'))
#print(str(manifest))
#env.Jar('sifter.jar', ['build', 'build/META-INF/MANIFEST.MF'], JARCHDIR='build')
env.Jar('sifter.jar', 'build', JARCHDIR='build')
#env.Depends('sifter.jar', manifest)
