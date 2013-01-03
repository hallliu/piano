package keyboard;

import org.freedesktop.cairo.*;
import org.gnome.gtk.*;
import org.gnome.gdk.*;

import java.util.HashSet;
import java.util.HashMap;
import synth.Synth;

public class Keyboard extends DrawingArea {
	private HashSet<Integer> pressed_white_keys, pressed_black_keys, playing;
	private int octave;
	private Synth fs;
	private static final double invnKeys = 1/42.0;
	private static final double blackOffset = invnKeys*2/3;
	private static final int keyboardShift = 2;
	private static final int keyWidth = 27;
	
	// a few key mappings
	private final HashMap<Integer,Integer> gtrans = new HashMap<Integer,Integer>(18);
	private final HashMap<Integer,Integer> keymap = new HashMap<Integer,Integer>(19);
	private final HashMap<Integer,Integer> white_rev_gtrans = new HashMap<Integer,Integer>(7);
	private final HashMap<Integer,Integer> black_rev_gtrans = new HashMap<Integer,Integer>(5);
	
	private class Tuple<X,Y> { //used a tuple for something in python, this is kinda necessary
		public final X x1;
		public final Y x2;
		public Tuple(X x1, Y x2) {
			this.x1 = x1;
			this.x2 = x2;
		}
	}
	
	public Keyboard(Synth fs) {
		super();
		this.fs = fs;
		this.octave = 5;
		this.pressed_white_keys = new HashSet<Integer>();
		this.pressed_black_keys = new HashSet<Integer>();
		this.playing = new HashSet<Integer>();
		this.addEvents(EventMask.or(EventMask.BUTTON_PRESS, EventMask.BUTTON_RELEASE));
		this.setCanFocus(true);
		this.setSizeRequest((int)(Keyboard.keyWidth/Keyboard.invnKeys), 120);
		
		this.connect(new Widget.ExposeEvent() {
			public boolean onExposeEvent(Widget arg0, EventExpose arg1) {
				return Keyboard.this.expose(arg0,arg1);
			}
		});
		this.connect(new Widget.ButtonPressEvent() {
			public boolean onButtonPressEvent(Widget arg0, EventButton arg1) {
				return Keyboard.this.btn_press(arg0,arg1);
			}
		});
		this.connect(new Widget.ButtonReleaseEvent() {
			public boolean onButtonReleaseEvent(Widget arg0, EventButton arg1) {
				return Keyboard.this.btn_release(arg0,arg1);
			}
		});
		this.connect(new Widget.KeyPressEvent() {
			public boolean onKeyPressEvent(Widget arg0, EventKey arg1) {
				return Keyboard.this.key_press(arg0,arg1);
			}
		});
		this.connect(new Widget.KeyReleaseEvent() {
			public boolean onKeyReleaseEvent(Widget arg0, EventKey arg1) {
				return Keyboard.this.key_release(arg0,arg1);
			}
		});
		
		initialize_const_maps();
	}
	
	public void clear_pressed_keys() {
		this.pressed_black_keys.clear();
		this.pressed_white_keys.clear();
	}
	
	public void ext_note_press(Integer[] noteVals) {
		for(int i=0; i<noteVals.length; ++i) {
			switch(noteVals[i]%12) {
			case 1:
			case 3:
			case 6:
			case 8:
			case 10:
				this.pressed_black_keys.add(gtrans.get(noteVals[i]%12)+(noteVals[i]/12)*7);
				break;
			default:
				this.pressed_white_keys.add(gtrans.get(noteVals[i]%12)+(noteVals[i]/12)*7);
				break;
			}
			this.fs.noteon(0, noteVals[i], 100);
		}
		redraw_board();
	}
	
	public void ext_note_release(Integer[] noteVals) {
		for(int i=0; i<noteVals.length; ++i) {
			switch(noteVals[i]%12) {
			case 1:
			case 3:
			case 6:
			case 8:
			case 10:
				this.pressed_black_keys.remove(gtrans.get(noteVals[i]%12)+(noteVals[i]/12)*7);
				break;
			default:
				this.pressed_white_keys.remove(gtrans.get(noteVals[i]%12)+(noteVals[i]/12)*7);
				break;
			}
			this.fs.noteoff(0, noteVals[i]);
		}
		redraw_board();
	}
	
	private boolean key_press(Widget widget, EventKey event) {
		int keyval = event.getKeyval().toUnicode();
		if (keyval < 58 && keyval > 47) {
			this.octave = keyval - 48;
			return true;
		}
		if (!keymap.containsKey(keyval)) {
			return true;
		}
		
		int note = keymap.get(keyval) + this.octave*12;
		
		switch(keymap.get(keyval)) {
		case 1: case 3: case 6: case 8: case 10:
		case 13: case 15: case 18:
			this.pressed_black_keys.add(gtrans.get(keymap.get(keyval)) + this.octave*7);
			break;
		default:
			this.pressed_white_keys.add(gtrans.get(keymap.get(keyval)) + this.octave*7);
			break;
		}
		
		if (!this.playing.contains(note)) {
			this.fs.noteon(0,note,100);
			this.playing.add(note);
		}
		
		redraw_board();
		return true;
	}
	
	private boolean key_release(Widget widget, EventKey event) {
		int keyval = event.getKeyval().toUnicode();
		if (keyval < 58 && keyval > 47) {
			return true;
		}
		if (!keymap.containsKey(keyval)) {
			return true;
		}
		int note = keymap.get(keyval) + this.octave*12;
		
		switch(keymap.get(keyval)) {
		case 1: case 3: case 6: case 8: case 10:
		case 13: case 15: case 18:
			this.pressed_black_keys.remove(gtrans.get(keymap.get(keyval)) + this.octave*7);
			break;
		default:
			this.pressed_white_keys.remove(gtrans.get(keymap.get(keyval)) + this.octave*7);
			break;
		}
		this.fs.noteoff(0,note);
		this.playing.remove(note);
		
		redraw_board();
		return true;
	}
	
	private boolean btn_press(Widget widget, EventButton event) {
		this.grabFocus();
		Tuple<String,Integer> key = calculate_key(event.getX(), event.getY());
		int note;
		if (key.x1.equals("white")) {
			this.pressed_white_keys.add(key.x2);
			note = white_rev_gtrans.get(key.x2%7) + key.x2/7*12;
		}
		else {
			this.pressed_black_keys.add(key.x2);
			note = black_rev_gtrans.get(key.x2%7) + key.x2/7*12;
		}
		
		if (!this.playing.contains(note)) {
			this.fs.noteon(0, note, 100);
			this.playing.add(note);
		}
		
		redraw_board();
		return true;
	}
	
	private boolean btn_release(Widget widget, EventButton event) {
		this.grabFocus();
		Tuple<String,Integer> key = calculate_key(event.getX(), event.getY());
		int note;
		if (key.x1.equals("white")) {
			this.pressed_white_keys.remove(key.x2);
			note = white_rev_gtrans.get(key.x2%7) + key.x2/7*12;
		}
		else {
			this.pressed_black_keys.remove(key.x2);
			note = black_rev_gtrans.get(key.x2%7) + key.x2/7*12;
		}

		this.fs.noteoff(0, note);
		this.playing.remove(note);

		redraw_board();
		return true;
	}
	
	private Tuple<String,Integer> calculate_key(double x, double y) {
		int width = this.getAllocation().getWidth();
		int height = this.getAllocation().getHeight();
		
		int key_pos = (int) (x/(invnKeys*width)); //nominal position of clicked key
		
		if (y > 0.6*height) {
			return new Tuple<String,Integer>("white", key_pos + keyboardShift*7);
		}
		int black_pos = (int) ((x-width*blackOffset)/(invnKeys*width));
		
		if ((x - width*blackOffset)/(invnKeys*width) - black_pos < 0.66) {
			switch(black_pos%7) {
			case 0: case 1: case 3: case 4: case 5:
				return new Tuple<String,Integer>("black", black_pos + keyboardShift*7);
			}
		}
		return new Tuple<String,Integer>("white", key_pos + keyboardShift*7);
	}
	
	private void redraw_board() {
		if (this.getWindow() != null) {
			Allocation alloc = this.getAllocation();
			Rectangle rect = new Rectangle(0,0,alloc.getWidth(),alloc.getHeight());
			this.getWindow().invalidate(rect, true);
		}
	}
	
	private boolean expose(Widget widget, EventExpose event) {
		Context cr = new Context(event);
		int width = this.getAllocation().getWidth();
		int height = this.getAllocation().getHeight();
		
		for(int i=0; i<(int)(1/invnKeys); i++) {
			cr.setSource(0, 0, 0);
			cr.moveTo(width*i*invnKeys, 0);
			cr.lineTo(width*i*invnKeys, height);
			cr.stroke();
			if (this.pressed_white_keys.contains(i+keyboardShift*7)) {
				cr.save();
				cr.rectangle(width*i*invnKeys, 0, width*invnKeys, height);
				cr.clip();
				cr.setSource(0, 0, 1);
				cr.paint();
				cr.restore();
			}
		}
		
		for(int i=0; i<(int)(1/invnKeys); i++) {
			switch(i%7) {
			case 0: case 1: case 3: case 4: case 5:
				if (this.pressed_black_keys.contains(i+keyboardShift*7)) {
					cr.setSource(0, 0, 1);
				}
				else {
					cr.setSource(0, 0, 0);
				}
				cr.rectangle(width*i*invnKeys+width*blackOffset, 0, width*blackOffset, height*0.6);
				cr.fill();
			}
		}
		return true;
	}
	
	void initialize_const_maps() { //puts the values inside the maps defined at the top
		gtrans.put(0,0);
		gtrans.put(1,0);
		gtrans.put(2,1);
		gtrans.put(3,1);
		gtrans.put(4,2);
		gtrans.put(5,3);
		gtrans.put(6,3);
		gtrans.put(7,4);
		gtrans.put(8,4);
		gtrans.put(9,5);
		gtrans.put(10,5);
		gtrans.put(11,6);
		gtrans.put(12,7);
		gtrans.put(13,7);
		gtrans.put(14,8);
		gtrans.put(15,8);
		gtrans.put(16,9);
		gtrans.put(17,10);
		gtrans.put(18,10);

		keymap.put(97,0);
		keymap.put(100,4);
		keymap.put(101,3);
		keymap.put(102,5);
		keymap.put(103,7);
		keymap.put(104,9);
		keymap.put(106,11);
		keymap.put(107,12);
		keymap.put(108,14);
		keymap.put(111,13);
		keymap.put(112,15);
		keymap.put(115,2);
		keymap.put(116,6);
		keymap.put(117,10);
		keymap.put(119,1);
		keymap.put(121,8);
		keymap.put(59,16);
		keymap.put(93,18);
		keymap.put(39,17);

		white_rev_gtrans.put(0,0);
		white_rev_gtrans.put(1,2);
		white_rev_gtrans.put(2,4);
		white_rev_gtrans.put(3,5);
		white_rev_gtrans.put(4,7);
		white_rev_gtrans.put(5,9);
		white_rev_gtrans.put(6,11);

		black_rev_gtrans.put(0,1);
		black_rev_gtrans.put(1,3);
		black_rev_gtrans.put(3,6);
		black_rev_gtrans.put(4,8);
		black_rev_gtrans.put(5,10);
	}
}