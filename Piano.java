package piano;

import synth.Synth;

import org.gnome.gdk.Event;
import org.gnome.gtk.*;

import keyboard.Keyboard;
import xplay.XPlay;

public class Piano {
	public Synth fs;
	public Keyboard kbd;
	public XPlay player;
	public Toolbar controls;
	public Entry mesNumberControl;
	
	public static void main(String args[]) {
		Synth fs = new Synth();
		fs.start();
		int sfid = fs.sfload("sound.sf2", 0);
		fs.program_select(0, sfid, 0, 0);
		Gtk.init(args);
		new Piano(fs, args[1]);
	}
	
	public Piano(Synth fs, String filename) {
		this.fs = fs;
		Window w = new Window();
		this.kbd = new Keyboard(fs);
		
		VBox topVBox = new VBox(false, 0);
		HBox toolbar = new HBox(false, 1);
		MenuBar menubar = makeMenuBar();
		this.controls = makeControls();
		
		Label measureLabel = new Label("Measure:");
		this.mesNumberControl = new Entry("1");
		mesNumberControl.setMaxLength(4);
		mesNumberControl.setWidthChars(4);
		
		toolbar.packStart(controls, true, true, 0);
		toolbar.packEnd(mesNumberControl, false, false, 0);
		toolbar.packEnd(measureLabel, false, false, 0);
		
		topVBox.packStart(menubar, false, false, 0);
		topVBox.packStart(toolbar, false, false, 0);
		topVBox.packStart(kbd, false, false, 0);
		
		w.add(topVBox);
		w.connect(new Window.DeleteEvent() {
			public boolean onDeleteEvent(Widget arg0, Event arg1) {
				quit();
				return false;
			}
		});
		w.showAll();
		try {
			Gtk.main();
		} catch (FatalError e) {
		}
	}
	
	private MenuBar makeMenuBar() {
		MenuBar menubar = new MenuBar();
		Menu fileMenu = new Menu();
		MenuItem fileMenuItem = new MenuItem("File");
		fileMenuItem.setSubmenu(fileMenu);
		
		MenuItem exitItem = new MenuItem("Exit");
		exitItem.connect(new MenuItem.Activate() {
			public void onActivate(MenuItem source) {
				quit();
			}
		});
		fileMenu.append(exitItem);
		
		MenuItem openItem = new MenuItem("Open");
		openItem.connect(new MenuItem.Activate() {
			public void onActivate(MenuItem source) {
				runDialog();
			}
		});
		fileMenu.append(openItem);
		
		menubar.append(fileMenuItem);
		return menubar;
	}
	
	private Toolbar makeControls() {
		Toolbar tb = new Toolbar();
		
		Image playIcon = new Image("icons/play.png");
		ToolButton playButton = new ToolButton(playIcon, "play");
		
		Image pauseIcon = new Image("icons/pause.png");
		ToolButton pauseButton = new ToolButton(pauseIcon, "pause");
		
		Image rewindIcon = new Image("icons/rewind.png");
		ToolButton rewindButton = new ToolButton(rewindIcon, "rewind");
		
		Image stopIcon = new Image("icons/stop.png");
		ToolButton stopButton = new ToolButton(stopIcon,"stop");
		
		tb.insert(playButton, 0);
		tb.insert(pauseButton, 1);
		tb.insert(rewindButton, 2);
		tb.insert(stopButton, 3);
		
		return tb;
	}
	
	private void runDialog() {
		FileChooserDialog fileD = new FileChooserDialog("Open...", null, FileChooserAction.OPEN);
		fileD.setCurrentFolder(".");
		ResponseType response = fileD.run();
		if (response.equals(ResponseType.OK)) {
			this.player = new XPlay(fileD.getFilename(), kbd, this.mesNumberControl);
			activateToolbarButtons();
			this.player.start();
		}
		fileD.destroy();
	}
	
	private void activateToolbarButtons() {
		Widget[] buttons = this.controls.getChildren();
		//activate the measure control
		this.mesNumberControl.connect(new Entry.Activate() {
			public void onActivate(Entry source) {
				Piano.this.player.seek();
			}
		});
		for (int i=0; i<buttons.length; i++) {
			ToolButton b = (ToolButton) buttons[i];
			switch(i) {
			case 0:
				b.connect(new ToolButton.Clicked() {
					public void onClicked(ToolButton source) {
						Piano.this.player.play();
					}
				});
				break;
			case 1:
				b.connect(new ToolButton.Clicked() {
					public void onClicked(ToolButton source) {
						Piano.this.player.pause();
					}
				});
				break;
			case 2:
				b.connect(new ToolButton.Clicked() {
					public void onClicked(ToolButton source) {
						Piano.this.player.rewind();
					}
				});
				break;
			case 3:
				b.connect(new ToolButton.Clicked() {
					public void onClicked(ToolButton source) {
						destroyPlayer();
					}
				});
				break;
			}
		}
	}
	
	private void deactivateControls() {
		System.out.println("deactivated");
		Widget[] buttons = this.controls.getChildren();
		this.mesNumberControl.connect(new Entry.Activate() {
			public void onActivate(Entry source) {}
		});
		for(int i=0; i<buttons.length; i++) {
			ToolButton b = (ToolButton) buttons[i];
			b.connect(new ToolButton.Clicked() {
				public void onClicked(ToolButton source) {}
			});
		}
	}
	
	public void destroyPlayer() {
		this.player.stopPlayer();
		deactivateControls();
		this.player = null;
	}
	public void quit() {
		if(this.player != null)	this.player.stopPlayer();
		Gtk.mainQuit();
	}
}
