package synth;

import com.sun.jna.Native;
import com.sun.jna.Library;
import com.sun.jna.Pointer;

public class Synth {
	public interface SynthLib extends Library {
		SynthLib INSTANCE = (SynthLib) Native.loadLibrary("fluidsynth",SynthLib.class);
		Pointer new_fluid_settings();
		Pointer new_fluid_synth(Pointer settings);
		Pointer new_fluid_audio_driver(Pointer settings, Pointer synth);
		int fluid_settings_setstr(Pointer settings, String name, String string);
		int fluid_settings_setnum(Pointer settings, String name, double val);
		void delete_fluid_audio_driver(Pointer driver);
		void delete_fluid_synth(Pointer synth);
		void delete_fluid_settings(Pointer settings);
		int fluid_synth_sfload(Pointer synth, String filename, int update_midi_presets);
		int fluid_synth_sfunload(Pointer synth, int sfid, int update_midi_presets);
		int fluid_synth_program_select(Pointer synth, int chan, int sfid, int bank, int preset);
		int fluid_synth_noteon(Pointer synth, int chan, int key, int vel);
		int fluid_synth_noteoff(Pointer synth, int chan, int key);
	}
	
	private Pointer synth;
	private Pointer settings;
	private Pointer audio_driver;
	
	public Synth() {
		Pointer settings = SynthLib.INSTANCE.new_fluid_settings();
		SynthLib.INSTANCE.fluid_settings_setnum(settings, "synth.gain", 0.2);
		SynthLib.INSTANCE.fluid_settings_setnum(settings, "synth.sample-rate", 44100);
		this.settings = settings;
		this.synth = SynthLib.INSTANCE.new_fluid_synth(this.settings);
	}
	
	public void start() {
		SynthLib.INSTANCE.fluid_settings_setstr(this.settings, "audio.driver", "alsa");
		this.audio_driver = SynthLib.INSTANCE.new_fluid_audio_driver(this.settings, this.synth);
	}
	
	public int program_select(int chan, int sfid, int bank, int preset) {
		return SynthLib.INSTANCE.fluid_synth_program_select(this.synth, chan, sfid, bank, preset);
	}
	
	public int sfload(String filename, int update_midi_presets) {
		return SynthLib.INSTANCE.fluid_synth_sfload(this.synth, filename, update_midi_presets);
	}
	
	public int noteon(int chan, int key, int vel) {
		return SynthLib.INSTANCE.fluid_synth_noteon(this.synth, chan, key, vel);
	}
	
	public int noteoff(int chan, int key) {
		return SynthLib.INSTANCE.fluid_synth_noteoff(this.synth, chan, key);
	}
	
	protected void finalize() {
		SynthLib.INSTANCE.delete_fluid_settings(this.settings);
		SynthLib.INSTANCE.delete_fluid_synth(this.synth);
		SynthLib.INSTANCE.delete_fluid_audio_driver(this.audio_driver);
	}
}
