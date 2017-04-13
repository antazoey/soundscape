/**
 *
 *  TarsosDSP is developed by Joren Six at
 *  The Royal Academy of Fine Arts & Royal Conservatory,
 *  University College Ghent,
 *  Hoogpoort 64, 9000 Ghent - Belgium
 *
 *  http://tarsos.0110.be/tag/TarsosDSP
 *
 **/
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import be.hogent.tarsos.dsp.AudioDispatcher;
import be.hogent.tarsos.dsp.AudioProcessor;
import be.hogent.tarsos.dsp.BlockingAudioPlayer;
import be.hogent.tarsos.dsp.PitchProcessor;
import be.hogent.tarsos.dsp.PitchProcessor.DetectedPitchHandler;
import be.hogent.tarsos.dsp.PitchProcessor.PitchEstimationAlgorithm;
import be.hogent.tarsos.dsp.example.InputPanel;
import be.hogent.tarsos.dsp.example.PitchDetectionPanel;
import be.hogent.tarsos.dsp.example.SpectrogramPanel;
import be.hogent.tarsos.dsp.util.FFT;

public class SoundSpace extends JFrame implements DetectedPitchHandler {

    /**
     *
     */
    private static final long serialVersionUID = 1383896180290138076L;
    private final SpectrogramPanel panel;
    private AudioDispatcher dispatcher;
    private Mixer currentMixer;
    private PitchEstimationAlgorithm algo;
    private double pitch;

    private float sampleRate = 44100;
    private int bufferSize = 1024 * 4;
    private int overlap = 768 * 4 ;

    private String fileName;

    private double myPitch;


    private ActionListener algoChangeListener = new ActionListener(){
        @Override
        public void actionPerformed(final ActionEvent e) {
            String name = e.getActionCommand();
            PitchEstimationAlgorithm newAlgo = PitchEstimationAlgorithm.valueOf(name);
            algo = newAlgo;
            try {
                setNewMixer(currentMixer);
            } catch (LineUnavailableException e1) {
                e1.printStackTrace();
            } catch (UnsupportedAudioFileException e1) {
                e1.printStackTrace();
            }
        }};

    public SoundSpace(String fileName){
        this.setLayout(new BorderLayout());
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setTitle("SoundSpace");
        panel = new SpectrogramPanel();
        algo = PitchEstimationAlgorithm.YIN;
        this.fileName = fileName;

        JPanel pitchDetectionPanel = new PitchDetectionPanel(algoChangeListener);

        JPanel inputPanel = new InputPanel();

        inputPanel.addPropertyChangeListener("mixer",
                new PropertyChangeListener() {
                    @Override
                    public void propertyChange(PropertyChangeEvent arg0) {
                        try {
                            setNewMixer((Mixer) arg0.getNewValue());
                        } catch (LineUnavailableException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        } catch (UnsupportedAudioFileException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                });

        JPanel containerPanel = new JPanel(new GridLayout(1,0));
        containerPanel.add(inputPanel);
      //  containerPanel.add(pitchDetectionPanel);
        this.add(containerPanel,BorderLayout.NORTH);

        JPanel otherContainer = new JPanel(new BorderLayout());
      // otherContainer.add(panel,BorderLayout.CENTER);
       // otherContainer.setBorder(new TitledBorder("3. Utter a sound (whistling works best)"));


        this.add(otherContainer,BorderLayout.CENTER);
    }



    private void setNewMixer(Mixer mixer) throws LineUnavailableException, UnsupportedAudioFileException {

        if(dispatcher!= null){
            dispatcher.stop();
        }
        if(fileName == null){
            final AudioFormat format = new AudioFormat(sampleRate, 16, 1, true,
                    false);
            final DataLine.Info dataLineInfo = new DataLine.Info(
                    TargetDataLine.class, format);
            TargetDataLine line;
            line = (TargetDataLine) mixer.getLine(dataLineInfo);
            final int numberOfSamples = bufferSize;
            line.open(format, numberOfSamples);
            line.start();
            final AudioInputStream stream = new AudioInputStream(line);

            // create a new dispatcher
            dispatcher = new AudioDispatcher(stream, bufferSize,overlap);
        } else {
            try {
                File audioFile = new File(fileName);
                dispatcher = AudioDispatcher.fromFile(audioFile, bufferSize, overlap);
                AudioFormat format = AudioSystem.getAudioFileFormat(audioFile).getFormat();
                dispatcher.addAudioProcessor(new BlockingAudioPlayer(format, bufferSize, overlap));
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        currentMixer = mixer;

        // JULIYA EDIT:  NOW HIDE THE INPUT PANEL



        // add a processor, handle pitch event.
        dispatcher.addAudioProcessor(new PitchProcessor(algo, sampleRate, bufferSize, overlap, 0, this));
        dispatcher.addAudioProcessor(fftProcessor);



        // run the dispatcher (on a new thread).
        new Thread(dispatcher,"Audio dispatching").start();
    }

    AudioProcessor fftProcessor = new AudioProcessor(){

        FFT fft = new FFT(bufferSize);
        float[] amplitudes = new float[bufferSize/2];


        @Override
        public boolean processFull(float[] audioFloatBuffer,
                                   byte[] audioByteBuffer) {
            processOverlapping(audioFloatBuffer,audioByteBuffer);
            return true;
        }

        public boolean std = false;
        Color color = new Color(0, 0, 0);
        int count = 0;
        @Override
        public boolean processOverlapping(float[] audioFloatBuffer,
                                          byte[] audioByteBuffer) {
            float[] transformbuffer = new float[bufferSize*2];
            System.arraycopy(audioFloatBuffer, 0, transformbuffer, 0, audioFloatBuffer.length);
            fft.forwardTransform(transformbuffer);
            fft.modulus(transformbuffer, amplitudes);

            //this i where i can gain access to pitch values

            int useable = 0;
            if (Math.abs(pitch) != 1) {
                useable = (int) Math.abs(Math.floor(pitch % 255));
            }
            if (useable != 0) {
                //System.out.println(useable);
                color = new Color(5693*useable%255, 4597*useable%255, 6521*useable%255, 61*useable%255);
            }
            StdDraw.enableDoubleBuffering();
            Turtle turtle1 = new Turtle(useable%.75,useable%.95,useable%60);
            turtle1.setPenColor(color);
            if (!std){
                turtle1.setCanvasSize((int) Math.ceil(Toolkit.getDefaultToolkit().getScreenSize().getWidth()), (int) Math.ceil(Toolkit.getDefaultToolkit().getScreenSize().getHeight()));
                turtle1.clear();
                std = true;
            }

            double step = Math.sqrt(3)/2/5;
            if(useable != 0) {
                count++;
                if (useable >= 100) {
                    turtle1 = new Turtle(useable%.9,useable%.9,useable&90);
                    turtle1.setPenRadius(.05);
                }
                else if(useable >= 43) {
                    turtle1 = new Turtle(useable%.9,useable%.9,useable%60);
                    turtle1.setPenRadius(.1);
                }
                else {
                    turtle1 = new Turtle(useable%.9,useable%.9,useable%60);
                    turtle1.setPenRadius(.4);
                }
                turtle1.goForward(-step);
                turtle1.turnLeft(-useable);
                color = new Color(4597*useable%255, 5693*useable%255, 6521*useable%255, 61*useable%255);
                turtle1.setPenColor(color);
                turtle1.goForward(step);
                turtle1.turnLeft(useable);
                color = new Color(4597*useable%255, 5693*useable%255, 6521*useable%255, 61*useable%255);
                turtle1.setPenColor(color);
                turtle1.goForward(step);
                turtle1.turnLeft(useable);
                color = new Color(4597*useable%255, 5693*useable%255, 6521*useable%255, 61*useable%255);
                turtle1.setPenColor(color);
                turtle1.turnLeft(useable);
                color = new Color(4597*useable%255, 5693*useable%255, 6521*useable%255, 61*useable%255);
                turtle1.setPenColor(color);
                turtle1.goForward(step);
                turtle1.turnLeft(useable);
                color = new Color(4597*useable%255, 5693*useable%255, 6521*useable%255, 61*useable%255);
                turtle1.setPenColor(color);
                turtle1.goForward(step);
                turtle1.turnLeft(-useable);
                color = new Color(4597*useable%255, 5693*useable%255, 6521*useable%255, 61*useable%255);
                turtle1.setPenColor(color);
            }
            if (count >= 20){
                turtle1.clear();
                count = 0;
            }
            turtle1.show();

          //  panel.drawFFT(pitch, amplitudes,fft);
            return true;
        }

        @Override
        public void processingFinished() {
            // TODO Auto-generated method stub
        }

    };

    @Override
    public void handlePitch(float pitch, float probability, float timeStamp,
                            float progress) {
        this.pitch = pitch;
    }

    public static void main(final String... strings) throws InterruptedException,
            InvocationTargetException {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager
                            .getSystemLookAndFeelClassName());
                } catch (Exception e) {
                    // ignore failure to set default look en feel;
                }
                //JFrame frame = strings.length == 0 ? new SoundSpace(null) : new SoundSpace(strings[0]) ;
                SoundSpace sp = new SoundSpace(null);
                JFrame frame = sp;
                frame.pack();
                frame.setSize(640, 480);
                frame.setVisible(true);
              //  System.out.print("Mine: ");
               // System.out.println(sp.panel.);
            }
        });
    }


}
