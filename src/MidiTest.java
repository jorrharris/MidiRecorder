import java.io.File;
import java.io.IOException;
import java.util.Vector;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiDevice.Info;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import javax.sound.midi.Transmitter;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

public class MidiTest extends Application{
	public static void main(String[] args) {
			launch(args);
	}
	
	Transmitter transmitter, transmitter2;
	Receiver receiver;
	Sequencer sequencer;
	MidiDevice device;
	Sequence sequence;
	Track currentTrack;
	MidiDevice oDevice;
	volatile Boolean connected = false;
	volatile Boolean recording = false;
	static int fileCounter;
	
	public void start(Stage primaryStage) throws MidiUnavailableException {
		sequencer = MidiSystem.getSequencer();
		sequencer.open();
		fileCounter = new File("midi/").list().length;
		
		Label select = new Label("Select Midi Device: ");
		ListView<Info> view = new ListView<>();
		Label fileLabel = new Label("Midi Files: ");
		ListView<File> fileView = new ListView<>();
		view.setPrefSize(100, 100);
		fileView.setPrefSize(200, 100);
		Info[] infos = MidiSystem.getMidiDeviceInfo();
		oDevice = MidiSystem.getMidiDevice(infos[0]);
		oDevice.open();
		for(int i=0;i<infos.length;i++)
		{
			if(MidiSystem.getMidiDevice(infos[i]).getMaxTransmitters() != 0  //&&
				//infos[i].getDescription().equalsIgnoreCase("External MIDI Port")
			  ) {
				view.getItems().add(infos[i]);
				//System.out.println(infos[i].getName() + " - " + infos[i].getDescription() + " " + MidiSystem.getMidiDevice(infos[i]).getMaxTransmitters());
			}
		}
		importFiles(fileView);
		
		fileView.setCellFactory(param -> new ListCell<File>(){
			@Override
			protected void updateItem(File file, boolean empty) {
				super.updateItem(file, empty);
				if(file != null)
					setText(file.getName());
			}
		});
		
		
		Button connect = new Button("Connect");
		Label connectionLabel = new Label("Connection: ");
		Circle connectionIndicator = new Circle(0, 16, 8, Color.BLACK);
		connectionIndicator.setId("circle");
		connectionIndicator.setStroke(Color.BLACK);
		Pane connectionPane = new Pane();
		connectionPane.getChildren().add(connectionIndicator);
		Label recordLabel = new Label("Recording: ");
		Pane recordPane = new Pane();
		Circle recordIndicator = new Circle(0, 16, 8, Color.BLACK);
		recordIndicator.setId("circle");
		recordIndicator.setStroke(Color.BLACK);
		recordPane.getChildren().add(recordIndicator);
		
		HBox hbox2 = new HBox(20, connect, connectionLabel, connectionPane, recordLabel, recordPane);
		hbox2.setAlignment(Pos.CENTER_LEFT);
		hbox2.setPadding(new Insets(20, 0, 0, 0));
		
		VBox vbox2 = new VBox(select, view, hbox2);
		VBox vbox3 = new VBox(fileLabel, fileView);
		
		HBox hbox3 = new HBox(20, vbox2, vbox3);
		
		Button record = new Button("Start Recording");
		Button stop = new Button("Stop Recording");
		Button play = new Button("Play Recording");
		
		HBox hbox = new HBox(15, record, stop, play);
		VBox vbox = new VBox(20, hbox3, hbox);
		vbox.setPadding(new Insets(20));
		
		RecordLight recordLight = new RecordLight(recordIndicator);
		Thread lightThread = new Thread(recordLight);
		
		Scene myScene = new Scene(vbox);
		myScene.getStylesheets().add("MidiTest.css");
		primaryStage.setScene(myScene);
		primaryStage.setTitle("Midi Recorder");
		primaryStage.show();
		
		connect.setOnAction(event -> {
			if(!view.getSelectionModel().isEmpty()) {
				try {
					device = MidiSystem.getMidiDevice(view.getSelectionModel().getSelectedItem());
					device.open();
					transmitter = device.getTransmitter();
					receiver = sequencer.getReceiver();
					transmitter.setReceiver(receiver);
					transmitter2 = device.getTransmitter();
					transmitter2.setReceiver(new DisplayReceiver());
					connectionIndicator.setId("greencircle");
					connected = true;
				} catch (MidiUnavailableException e) {
					System.out.println("Something went wrong with Connecting the Device");
				}
			}
		});
		
		record.setOnAction(event -> {
			if(connected == true) {
				try {
					recording = true;
					if(!lightThread.isAlive()) {
						lightThread.start();
					}
					sequence = new Sequence(Sequence.PPQ, 24);
					currentTrack = sequence.createTrack();
					sequencer.setSequence(sequence);
					sequencer.setTickPosition(0);
					sequencer.recordEnable(currentTrack, -1);
					sequencer.startRecording();
				} catch (InvalidMidiDataException e) {
					System.out.println("Invalid Midi Data");
				}

			}
		});
		
		stop.setOnAction(event -> {
			recording = false;
			if(sequencer.isRecording()) {
					sequencer.stopRecording();
					Sequence temp = sequencer.getSequence();
					write(temp);
					fileCounter++;
					importFiles(fileView);
					System.out.println("Wrote it");
			}
		});
		
		play.setOnAction(event -> {
			if (!fileView.getSelectionModel().isEmpty()) {
				try {
					sequencer.setSequence(MidiSystem.getSequence(fileView.getSelectionModel().getSelectedItem()));
					sequencer.start();
				} catch (InvalidMidiDataException e) {
					System.out.print("Invalid Midi Data");
				} catch (IOException e) {
					System.out.println("IOException");
				}
			}
		});
		
		primaryStage.setOnCloseRequest(event -> {
				try {
					device.close();
					sequencer.close();
					receiver.close();
					transmitter.close();
					transmitter2.close();
					System.out.println("Connections Closed");
				}
				catch (Exception e){
					System.out.println("Error Closing Connection / Connections not established");
				}
		        Platform.exit();
		        System.exit(0);
		    });
	}
	
	public void importFiles(ListView<File> fileViewer) {
		File[] fList = new File("midi/").listFiles();
		for(int i = 0; i < fList.length; i++) {
			if(!fileViewer.getItems().contains(fList[i])) {
				fileViewer.getItems().add(fList[i]);
			}
		}
	}

	//this method takes the midi-in sequence and cuts out the empty space at the beginning.
	//it also writes the new midi file to the midi directory
	public static void write(Sequence s) {
	      int i, j;
	      int eventStart = 0;
	      Vector<int[]> notes= new Vector<int[]>();
	      try {
	    	  int difference = 0;
		      Sequence tempSequence = new Sequence(Sequence.PPQ, 24);
	  	      Track tempTrack = tempSequence.createTrack();
		      //long trackLength = s.getMicrosecondLength()/s.getTickLength();
		      Track[] trackList = s.getTracks();
		      //System.out.println("has tracks "+trackList.length);
		      //i holds the track number
		      for(i = 0; i < trackList.length; i++) {
		          int l = trackList[i].size()-1;
		          //System.out.println(i+" has events "+l);
		          MidiEvent event = null;
		          ShortMessage sm = null;
		          byte[] data = null;
		          
		          //j holds the events in the track
		          for(j = 0; j < l; j++) {
			          event = trackList[i].get(j);
			          if(event.getMessage().getStatus() == MetaMessage.META) { 
			        	  System.out.println("meta "); 
			          	  continue; 
			          }
			          sm = (ShortMessage)event.getMessage();
			          //System.out.println("event "+sm.getCommand()+" "+sm.getStatus()+" "+sm.getData1()+" "+sm.getData2());
			          data = sm.getMessage();
			          //for(int ik = 0; ik < data.length; ik++) System.out.print(data[ik]+" ");
			          //System.out.println();
			          eventStart = (int)event.getTick();
			          int command = sm.getCommand();
			          int key = sm.getData1();
			          int vel = sm.getData2();
			          //System.out.println(j + " " + (tick2-tick) + " " + trackLength + " " + tick2 +"  " + tick + " ch " + sm.getChannel() + " " + key);
			          if(j == 0) { 
			        	  difference = (int)event.getTick();
			          }
				      notes.add(new int[]{command, key, vel, eventStart});  
		          }
	          }
		      //i holds the notes from the Vector array "notes"
	          for(i = 0; i < notes.size(); i++) {
		          int[] singleNote = notes.get(i);
		          for(int k = 0; k < 4; k++) {
		        	  System.out.print(singleNote[k] + " ");
		          }
		          System.out.println();
		          if(singleNote == null) 
		              break;
		          ShortMessage newMessage = new ShortMessage();
		          newMessage.setMessage(singleNote[0], 0, singleNote[1], singleNote[2]);
		          /*
		          ShortMessage off = new ShortMessage();
		          off.setMessage(ShortMessage.NOTE_OFF, 0, singleNote[0], singleNote[1]);
		          */
		          tempTrack.add(new MidiEvent(newMessage, singleNote[3] - difference));
		          //tempTrack.add(new MidiEvent(off, singleNote[2] + singleNote[3]));
	          }
	          File file = new File("midi/", "MyMidiFile" + fileCounter + ".mid");
	          MidiSystem.write(tempSequence, 0, file);
	      }
	      catch(Exception e) { e.printStackTrace(); }
	  }
	
	class RecordLight implements Runnable{
		Circle c;
		
		public RecordLight(Circle circle) {
			c = circle;
		}
		
		@Override
		public void run() {
			while (true) {
				if(connected == true && recording == true) {
					try {
						c.setId("redcircle");
						Thread.sleep(800);
						c.setId("circle");
						Thread.sleep(500);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			//System.out.println("Hey");
			}
		}
	}
	
	public class DisplayReceiver implements Receiver{
		@Override
		public void send(MidiMessage message, long timeStamp) {
			if (message instanceof ShortMessage) {
				ShortMessage sm = (ShortMessage)message;
				int channel = sm.getChannel();
				
				if(sm.getCommand() == ShortMessage.NOTE_ON) {
					int key = sm.getData1();
					int velocity = sm.getData2();
					Note note = new Note(key);
					System.out.println(note);
				}
				else if (sm.getCommand() == ShortMessage.NOTE_OFF) {
					/*
		            int key = sm.getData1();
		            int velocity = sm.getData2();
		            Note note = new Note(key);
		            System.out.println(note);
		            */
		        }
		        else {
		            System.out.println("Command:" + sm.getCommand());
		        }
			}
		}

		@Override
		public void close() {
			
		}
	}
	
	public class Note {

	    private final String[] NOTE_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

	    private String name;
	    private int key;
	    private int octave;

	    public Note(int key) {
	        this.key = key;
	        this.octave = (key / 12)-1;
	        int note = key % 12;
	        this.name = NOTE_NAMES[note];
	    }

	    @Override
	    public boolean equals(Object obj) {
	        return obj instanceof Note && this.key == ((Note) obj).key;
	    }

	    @Override
	    public String toString() {
	        return "Note -> " + this.name;
	    }
	}
	
}

