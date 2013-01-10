package xplay;

import java.util.*;

import keyboard.Keyboard;
import org.gnome.gtk.Entry;

import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;

public class XPlay extends Thread {
	private static final HashMap<Character,Integer> noteDict = new HashMap<Character,Integer>();
	private static final HashMap<Character,Integer> sharpMap = new HashMap<Character,Integer>();
	private static final char[] circleOfFifths = {'f','c','g','d','a','e','b'};
	
	private static int noteToValue(String note) {
		int value = noteDict.get(note.charAt(0))+12*Integer.valueOf(note.substring(1,2));
		if (note.length() == 3) {
			value += sharpMap.get(note.charAt(2));
		}
		return value;
	}
	
	private static String keyTransform(String note, int key) {
		if(note.length() == 3) return note;
		if (key < 0) {
			char [] flatRange = Arrays.copyOfRange(circleOfFifths,7+key,7);
			Arrays.sort(flatRange);
			if(Arrays.binarySearch(flatRange,note.charAt(0)) >= 0) return note.concat("@");
		}
		if (key > 0) {
			char [] sharpRange = Arrays.copyOfRange(circleOfFifths,0,key);
			Arrays.sort(sharpRange);
			if(Arrays.binarySearch(sharpRange, note.charAt(0)) >= 0) return note.concat("#");
		}
		return note;
	}
	
//	private static double[] strToDblArr(String s) {
//		String[] strs = s.split(",");
//		double[] result = new double[strs.length];
//		for(int i=0; i<strs.length; i++) {
//			result[i] = Double.valueOf(strs[i]);
//		}
//		return result;
//	}
	
	private static boolean flEquals(double d1, double d2) {
		return (Math.abs(d1-d2) < 0.00001);
	}
	
	private class NoteBeat implements Comparable<NoteBeat> {
		public double beatValue;
		public int tempo;
		public LinkedList<Integer> notesOn;
		public LinkedList<Integer> notesOff;
		
		NoteBeat(double beatValue, int tempo) {
			this.beatValue=beatValue;
			this.tempo = tempo;
			this.notesOn = new LinkedList<Integer>();
			this.notesOff = new LinkedList<Integer>();
		}
		
		public void addNoteOn(String note) {
			this.notesOn.add(noteToValue(note));
		}
		
		public void addNoteOff(String note) {
			this.notesOff.add(noteToValue(note));
		}
		
		public int compareTo(NoteBeat y) {
			if (flEquals(this.beatValue, y.beatValue)) return 0;
			return (new Double(this.beatValue)).compareTo(y.beatValue);
		}
	}
	
	//instance declarations start here
	private Entry mesNumberControl;
	private Keyboard keyboard;
	private ArrayList<NoteBeat> listOfBeats;
	private Object isPausedEvent;
	private boolean isPaused;
	
	private double beatPosition;
	private int measure = 0;
	private boolean hasSeeked = false;
	private LinkedList<Vector<Integer>> timesigChanges; //holds a map from measure number to time signature only on the change
	
	public XPlay(String filename, Keyboard keyboard, Entry mesNumberControl) {
		super();
		this.keyboard = keyboard;
		this.mesNumberControl = mesNumberControl;
		this.listOfBeats = new ArrayList<NoteBeat>();
		this.isPausedEvent = new Object();
		this.isPaused = true;
		this.timesigChanges = new LinkedList<Vector<Integer>>();
		
		populateMaps();
		//pre-emptively read the whole file into a structure
		try {
			parseFile(filename);
		} catch (IOException e) {
			System.out.println("Error with file");
			e.printStackTrace();
		}
	}
	
	public void run() {
		double beatInMeasure = this.listOfBeats.get(0).beatValue;
		for (int i=0; i<listOfBeats.size() && !this.isInterrupted(); i++) {
			NoteBeat b;
			while(this.isPaused) {
				synchronized(this.isPausedEvent) {
					try {
						this.isPausedEvent.wait();
					} catch (InterruptedException e) {
						this.interrupt();
					}
				}
			}
			if(this.isInterrupted()) break;
			
			//if we seeked while paused, take care of it now
			if(this.hasSeeked) {
				//search for the beat position
				int position = Collections.binarySearch(this.listOfBeats, new NoteBeat(this.beatPosition, 0));
				i = (position >= 0)?position:(-position-1);
				b = listOfBeats.get(i);
				beatInMeasure = (this.beatPosition<0)?this.beatPosition:0;
				this.hasSeeked = false;
			} else {
				//update the measure number, accounting for time sig
				b = listOfBeats.get(i);
				int timesig = 0; //hold the current time sig
				for(Vector<Integer> v : this.timesigChanges) {
					if(v.firstElement()>measure) break;
					timesig = v.lastElement();
				}
				try {
					beatInMeasure += b.beatValue - this.listOfBeats.get(i-1).beatValue;
				} catch (ArrayIndexOutOfBoundsException e) {
					beatInMeasure = b.beatValue;
				}
				if(flEquals(beatInMeasure, (double) timesig) || beatInMeasure > (double) timesig) {
					measure++;
					beatInMeasure = beatInMeasure - timesig;
					this.mesNumberControl.setText(Integer.toString(measure + 1));
				}
			}
			//make the sounds
			this.keyboard.ext_note_release(b.notesOff.toArray(new Integer[0]));
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				break;
			}
			this.keyboard.ext_note_press(b.notesOn.toArray(new Integer[0]));
			double beatFraction;
			try {
				beatFraction = this.listOfBeats.get(i+1).beatValue - b.beatValue;
			} catch (IndexOutOfBoundsException e1) {
				//having reached the end of our piece, we wait, turn off the notes, and hold for instructions
				beatFraction = 1;
				this.keyboard.clear_pressed_keys();
				this.isPaused = true;
				while(this.isPaused) {
					synchronized (this.isPausedEvent) {
						try {
							this.isPausedEvent.wait();
							System.out.println("wait finished");
							i = 0;
						} catch (InterruptedException e) {
							this.interrupt();
						}
					}
				}
			}
			double waitTime = 60000*beatFraction/b.tempo - 50;
			try {
				Thread.sleep((long) waitTime);
			} catch (InterruptedException e) {
				break;
			}
		}
	}
	public void play() {
		this.isPaused = false;
		synchronized(this.isPausedEvent) {
			this.isPausedEvent.notifyAll();
		}
	}
	public void pause() {
		this.isPaused = true;
	}
	public void seek() {
		int beatCounter = 0;
		this.measure = Integer.valueOf(this.mesNumberControl.getText()) - 1;
		for(int i=1; i<this.timesigChanges.size()+1; i++) { //we want to start from 1
			int nextChange;
			try {
				nextChange = this.timesigChanges.get(i).firstElement();
			} catch (IndexOutOfBoundsException e) {
				nextChange = Integer.MAX_VALUE;
			}
			int prevChange = this.timesigChanges.get(i-1).firstElement();
			if(nextChange < measure) {
				//if the next time change is before the measure
				beatCounter += (nextChange - prevChange) * this.timesigChanges.get(i-1).lastElement();
			}
			else {
				//measure lies somewhere in between the prev change and the next change
				beatCounter += (measure-prevChange)*this.timesigChanges.get(i-1).lastElement();
				break;
			}
		}
		this.keyboard.clear_pressed_keys();
		this.beatPosition = (double) beatCounter;
		this.hasSeeked = true;
	}
	
	public void rewind() {
		this.mesNumberControl.setText("1");
		this.measure = 0;
		this.beatPosition = this.listOfBeats.get(0).beatValue;
		this.keyboard.clear_pressed_keys();
		this.hasSeeked = true;
	}
	
	public void stopPlayer() {
		this.keyboard.clear_pressed_keys();
		this.interrupt();
	}

	private void parseFile(String filename) throws IOException {
		BufferedReader fileIn = new BufferedReader(new FileReader(filename));
		String line;
		//some persistent loop variables
		int tempo = 120; 
		int key = 0;
		int timesig = 4;
		int measure = 0;
		while((line = fileIn.readLine()) != null) {
			//we assume that listOfNotes is sorted.
			if (line.charAt(0) == '#' || line.equals("")) continue;
			String[] lineTokens = line.split(" ");
			
			switch(lineTokens[0]) {
			case "tempo":
				tempo = Integer.valueOf(lineTokens[1]);
				continue;
			case "key":
				key = Integer.valueOf(lineTokens[1]);
				continue;
			case "timesig":
				timesig = Integer.valueOf(lineTokens[1]);
				//put timesig into the global list
				Vector<Integer> measureSig = new Vector<Integer>(2);
				measureSig.add(measure);
				measureSig.add(timesig);
				this.timesigChanges.add(measureSig); //note that this always preserves order, as measure is monotone increasing
				continue;
			case "measure":
				measure = Integer.valueOf(lineTokens[1]) - 1;
				continue;
			}
			//now assuming the note start duration format
			String noteVal = keyTransform(lineTokens[0], key);
			double start = Double.valueOf(lineTokens[1])+ measure*timesig;
			double end = start + Double.valueOf(lineTokens[2]);
			//create two new NoteBeat nodes for start and end in case we need them
			NoteBeat startNote = new NoteBeat(start, tempo);
			NoteBeat endNote = new NoteBeat(end, tempo);
			//look for the start and the end in the list of preexisting ones
			int startPosition = Collections.binarySearch(this.listOfBeats, startNote);
			//process them
			if (startPosition < 0) { //insert new node into arraylist
				startNote.addNoteOn(noteVal);
				this.listOfBeats.add(-startPosition-1, startNote);
			}
			else { //add this note to the noteOn list in pre-existing node
				this.listOfBeats.get(startPosition).addNoteOn(noteVal);
			}
			
			int endPosition = Collections.binarySearch(this.listOfBeats, endNote);
			if (endPosition < 0) {
				endNote.addNoteOff(noteVal);
				this.listOfBeats.add(-endPosition-1, endNote);
			}
			else {
				this.listOfBeats.get(endPosition).addNoteOff(noteVal);
			}
		}
	}
	
	private void populateMaps() {
		noteDict.put('a',9);
		noteDict.put('c',0);
		noteDict.put('b',11);
		noteDict.put('e',4);
		noteDict.put('d',2);
		noteDict.put('g',7);
		noteDict.put('f',5);

		sharpMap.put('#',1);
		sharpMap.put('@',-1);
		sharpMap.put('n',0);
	}
}