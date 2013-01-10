CLASSPATH=/usr/share/java-gnome-4.0/lib/gtk.jar:/usr/share/jna/lib/jna.jar:.
JOPTS=-classpath $(CLASSPATH)
JC=javac
.SUFFIXES: .java .class
.java.class: 
	$(JC) $(JOPTS) $*.java

CLASSES= synth/Synth.java keyboard/Keyboard.java xplay/XPlay.java piano/Piano.java 
default: classes
classes: $(CLASSES:.java=.class)
run: 
	java $(JOPTS) piano.Piano default.mus2
clean: 
	rm *.class
