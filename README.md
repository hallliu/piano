piano
=====

A short and simple piano written in Java. Can use mouse and ASDF keys to play notes (use number keys to change octave).
Also plays songs written in a specific format. Example is in wrapup.mus2 (Winter Wrap Up from MLP:FiM)

Requires JNA, Java GTK bindings, and FluidSynth. Edit classpath in the makefile appropriately for your system. 
Additionally, a sound font file is required. Grab one from somewhere and save it as sound.sf2 in the top level directory

Compile by running make. Run with make run.
