package com.marklipson.musicgen;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

import com.marklipson.musicgen.WaveSource.SmoothValue;

public class HypnoTuner
{
  String infoMessage = 
      "HypnoTuner is a trance/meditation tone generator.\n\n" +
      "Two different tones slowly alternate between the left and right channels\n" +
      "of your headphones or sound system.  When listening through headphones,\n" +
      "a mild interference pattern between the two frequencies should result,\n" +
      "causing some mild and hopefully pleasant brain-jiggling.\n" +
      "\n" +
      "1) put on your headphones\n" +
      "2) adjust 'A' to a frequency you like\n" +
      "3) adjust other sliders one at a time, listening carefully, until it feels right\n" +
      "4) groove on it for a while\n" +
      "\n" +
      "Suggestions, etc: http://helianthusinc.com/contact.php";
  
  // number of harmonics that are controllable
  int nHarmonics = 16;
  WaveSource wave = new WaveSource( nHarmonics );

  // output
  Speakers speakers;
  JFrame frame;
  JSlider toneA;
  JSlider diffBlo, diffBhi;
  JSlider beatCycle;
  JSlider balCycle;
  JSlider harmonics[];
  JSlider customLevel;
  JTextField customFunction;
  JToggleButton btn_mute, btnRecord, btnAudioRecord;
  boolean initialLoad;
  
  JPanel lightBox;
  Set<PersistedValue> persisted = new HashSet<PersistedValue>();
  
  File settingsFile;
  File currentDir;
  
  class PersistedValue
  {
    String name;
    JComponent target;
    public PersistedValue( String name, JComponent target )
    {
      this.name = name;
      this.target = target;
    }
    private Object getValue()
    {
      if (target instanceof JSlider)
        return ((JSlider)target).getValue();
      if (target instanceof JTextComponent)
        return ((JTextComponent)target).getText();
      return null;
    }
    void save( Properties props )
    {
      Object value = getValue();
      props.setProperty( name, String.valueOf( value ) );
    }
    void save( JSON props )
    {
      Object value = getValue();
      props.set( name, value );
    }
    void load( Properties props )
    {
      String strValue = props.getProperty( name );
      if (strValue == null)
        return;
      if (! strValue.matches( "\\-?\\d+" ))
        return;
      setValue(strValue);
    }
    private void setValue(Object value)
    {
      if (value == null)
        return;
      if (target instanceof JSlider)
      {
        try
        {
          int v = Integer.parseInt( value.toString() );
          ((JSlider)target).setValue( v );
        }
        catch( NumberFormatException x )
        {
        }
      }
      else if (target instanceof JTextComponent)
        ((JTextComponent)target).setText( value.toString() );
    }
    void load( JSON props )
    {
      Object v = props.get( name );
      setValue( v );
    }
  }
  
  void pulseLight()
  {
    new Thread()
    {
      void update()
      {
        //System.out.println( tOffset );
        double tBal = wave.track_balance.retrieve();
        double tBeat = wave.track_beat.retrieve();
        setLevel( -Math.sin( tBeat ), Math.sin( tBal ) );
      }
      public void run()
      {
        while (! Thread.interrupted())
        {
          try
          {
            Thread.sleep( 10 );
            update();
          }
          catch( InterruptedException x )
          {
            break;
          }
        }
      }
    }.start();
  }
  void setLevel( double pos, double p2 )
  {
    Graphics g = lightBox.getGraphics();
    int w = lightBox.getWidth();
    int h = lightBox.getHeight();
    int h1 = 50;//(w/8 - 20);
    int h0 = w - h1 - 100;
    int r1 = 11;
    int r2 = 9;
    g.setColor( lightBox.getBackground() );
    g.fillRect( 0, 0, w, h );
    g.setColor( new Color(248,248,248) );
    g.fillRoundRect( h0-h1-r1/2, 0, h1*2+r1, h, 6, 6 );
    int x = (int)(h0 + h1 * pos);
    g.setColor( new Color(192,224,240) );
    g.fillOval( x - r1/2, h/4 - r1/2, r1, r1 );
    //
    int x2 = (int)(h0 + h1 * p2);
    g.setColor( new Color(192,240,192) );
    g.fillOval( x2 - r2/2, h*3/4 - r2/2, r2, r2 );
  }
  
  HypnoTuner( File storeSettingsHere )
  {
    this.settingsFile = storeSettingsHere;
  }
  
  public void autoSaveSettings()
  {
    Thread autoSaveSettings = new Thread( "auto-save settings" )
    {
      @Override
      public void run()
      {
        try
        {
          JSON oldSettings = getCurrentSettings();
          for (;;)
          {
            Thread.sleep( 2000 );
            if (settingsFile != null)
            {
              JSON settings = getCurrentSettings();
              if (settings.equals( oldSettings ))
                continue;
              try
              {
                FileWriter w = new FileWriter( settingsFile );
                w.write( settings.toString() );
                w.close();
              }
              catch( IOException x )
              {
                x.printStackTrace( System.err );
              }
            }
          }
        }
        catch( InterruptedException x )
        {
        }
      }
    };
    autoSaveSettings.start();
  }
  protected void restoreSettings()
  {
    if (settingsFile != null)
    {
      JSON settings = JSON.loadFromFile( settingsFile );
      if (settings == null)
        return;
      initialLoad = true;
      JSON controls = settings.getComplex( "controls" );
      if (controls != null)
        setSaveState( controls );
      initialLoad = false;
      String dir = settings.getString( "folder" );
      if (dir != null)
        currentDir = new File( dir );
    }
  }
  protected JSON getCurrentSettings()
  {
    JSON settings = JSON.createObject();
    JSON controls = JSON.createObject();
    getSaveState( controls );
    settings.set( "controls", controls );
    if (currentDir != null)
      settings.set( "folder", currentDir.toString() );
    return settings;
  }
  public void setupSound() throws Exception
  {
    System.out.println( "Acquiring audio line (e.g. speakers)" );
    speakers = new Speakers( wave.getRate() );
    wave.setAudioTarget( speakers );
  }
  void buildFrame()
  {
    frame = new JFrame( "HypnoTuner" );
    frame.setSize( 1000, 500 );
    JRootPane root = frame.getRootPane();
    root.setLayout( new GridLayout( 13 + nHarmonics, 1 ) );
    toneA = new JSlider( 2500, 5500, 3723 );
    toneA.setToolTipText( "base frequency" );
    diffBlo = new JSlider( -2500, 4000, 832 );
    diffBhi = new JSlider( -2500, 4000, 1072 );
    diffBlo.setToolTipText( "secondary frequency varies around base frequency by this much on the low end" );
    diffBhi.setToolTipText( "secondary frequency varies around base frequency by this much on the high end" );
    balCycle = new JSlider( -1500, 5000, 2861 );
    beatCycle = new JSlider( -1500, 5000, 3000 );
    balCycle.setToolTipText( "The base and secondary frequencies alternate between the left and right channels.  This parameter controls how long this cycle is." );
    beatCycle.setToolTipText( "The secondary frequency varies between the low and high values in a cycle lasting this many seconds." );
    harmonics = new JSlider[nHarmonics];
    harmonics[0] = new JSlider( 0, 100000, 90000 );
    harmonics[0].setToolTipText( "Percentage of 1st harmonic to include in both base and secondary tones." );
    harmonics[1] = new JSlider( 0, 100000, 43928 );
    harmonics[1].setToolTipText( "Percentage of 2nd harmonic to include in both base and secondary tones." );
    harmonics[2] = new JSlider( 0, 100000, 3000 );
    harmonics[2].setToolTipText( "Percentage of 3rd harmonic to include in both base and secondary tones." );
    for (int nH=3; nH < nHarmonics; nH++)
    {
      harmonics[nH] = new JSlider( 0, 100000, 0 );
      harmonics[nH].setToolTipText( "Percentage of " + (nH+1) + "th harmonic." );
      // TODO different color
    }
    customLevel = new JSlider( 0, 100000, 0 );
    customLevel.setToolTipText( "Level for custom waveform" );
    customFunction = new JTextField();
    customFunction.setToolTipText( "Custom waveform - f(t)" );
    customFunction.getDocument().addDocumentListener( new DocumentListener()
    {
      private void update()
      {
        String expr = customFunction.getText();
        try
        {
          TimeFunction.CompiledFunction fn = TimeFunction.compile( expr );
          fn.autoSetScale();
          if (fn != null)
            wave.setCustomFunction( fn );
        }
        catch( Exception x )
        {
        }
      }
      @Override
      public void removeUpdate(DocumentEvent e)
      {
        update();
      }
      @Override
      public void insertUpdate(DocumentEvent e)
      {
        update();
      }
      @Override
      public void changedUpdate(DocumentEvent e)
      {
        update();
      }
    });
    
    root.add( decorateSlider( "A: (hz)", toneA, 0.001, true, wave.vA ) );
    root.add( decorateSlider( "B-delta-lo: (hz)", diffBlo, 0.001, true, wave.vBlo ) );
    root.add( decorateSlider( "B-delta-hi: (hz)", diffBhi, 0.001, true, wave.vBhi ) );
    root.add( decorateSlider( "beatCycle: (s)", beatCycle, 0.001, true, wave.vBeatCycle ) );
    root.add( decorateSlider( "balanceCycle: (s)", balCycle, 0.001, true, wave.vBalCycle ) );
    lightBox = new JPanel();
    for (int nh=0; nh < harmonics.length; nh++)
      root.add( decorateSlider( "h(" + (nh+1) + "):", harmonics[nh], 0.001, false, wave.vH[nh] ) );
    root.add( decorateSlider( "custom level:", customLevel, 0.00001, false, wave.customLevel ) );
    root.add( decorateOther( "custom function:", customFunction ) );
    root.add( new JLabel("") );
    root.add( new JLabel("") );
    Box controls = Box.createHorizontalBox();
    controls.add( Box.createHorizontalStrut( 10 ) );
    // PLAYLIST
    final PlayList playlist = new PlayList( this );
    controls.add( playlist );
    // SAVE
    JButton btn = new JButton( "save" );
    btn.addActionListener( new ActionListener()
    {
      @Override
      public void actionPerformed( ActionEvent e )
      {
        JSON saved = JSON.createObject();
        getSaveState( saved );
        JFileChooser c = new JFileChooser();
        c.setCurrentDirectory( currentDir );
        c.setDialogTitle( "Save Settings" );
        if (c.showSaveDialog( frame ) == JFileChooser.APPROVE_OPTION)
        {
          File saveAs = c.getSelectedFile();
          if (saveAs.exists())
            if (JOptionPane.showConfirmDialog( frame, "File exists, overwrite?" ) != JOptionPane.OK_OPTION)
              return;
          try
          {
            FileWriter w = new FileWriter( saveAs );
            String str = saved.prettyPrint();
            w.write( str );
            w.close();
          }
          catch( Exception x )
          {
            JOptionPane.showMessageDialog( frame, x );
          }
        }
        currentDir = c.getCurrentDirectory();
      }
    });
    controls.add( btn );
    // LOAD
    btn = new JButton( "load" );
    btn.addActionListener( new ActionListener()
    {
      @Override
      public void actionPerformed( ActionEvent e )
      {
        JFileChooser c = new JFileChooser();
        c.setCurrentDirectory( currentDir );
        c.setDialogTitle( "Load Settings or Playlist" );
        if (c.showOpenDialog( frame ) == JFileChooser.APPROVE_OPTION)
        {
          File loadFrom = c.getSelectedFile();
          try
          {
            JSON json = JSON.loadFromFile( loadFrom );
            if (json != null)
            {
              if (json.isArray())
              {
                playlist.loadPlaylist( loadFrom.getName(), json );
                playlist.play( true );
              }
              else
                setSaveState( json );
            }
            else
            {
              FileReader r = new FileReader( loadFrom );
              //FIXME detect JSON
              Properties props = new Properties();
              props.load( r );
              r.close();
              setSaveState( props );
            }
          }
          catch( Exception x )
          {
            JOptionPane.showMessageDialog( frame, x );
          }
        }
        currentDir = c.getCurrentDirectory();
      }
    });
    controls.add( btn );
    JButton info = new JButton( "info" );
    info.addActionListener( new ActionListener()
    {
      @Override
      public void actionPerformed( ActionEvent e )
      {
        JOptionPane.showMessageDialog( frame, infoMessage, "HypnoTuner", JOptionPane.PLAIN_MESSAGE );
      }
    });
    controls.add( info );
    // RECORD
    btnRecord = new JToggleButton( "record" );
    btnRecord.addActionListener( new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent e)
      {
        if (btnRecord.isSelected())
        {
          // start recording
          btnRecord.setText( "RECORDING" );
          controlRecording( true );
        }
        else
        {
          // save recording
          String dataToRecord = recordedSettings.toString();
          btnRecord.setText( "record" );
          controlRecording( false );
          JFileChooser c = new JFileChooser();
          c.setDialogTitle( "Save Recording" );
          c.setCurrentDirectory( currentDir );
          if (c.showSaveDialog( frame ) == JFileChooser.APPROVE_OPTION)
          {
            File saveAs = c.getSelectedFile();
            if (saveAs.exists())
              if (JOptionPane.showConfirmDialog( frame, "File exists, overwrite?" ) != JOptionPane.OK_OPTION)
                return;
            try
            {
              FileWriter w = new FileWriter( saveAs );
              w.write( dataToRecord );
              w.close();
            }
            catch( Exception x )
            {
              JOptionPane.showMessageDialog( frame, x );
            }
          }
          currentDir = c.getCurrentDirectory();
        }
      }
    });
    controls.add( btnRecord );
    // AUDIO RECORD
    btnAudioRecord = new JToggleButton( "stream" );
    btnAudioRecord.addActionListener( new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent e)
      {
        if (btnAudioRecord.isSelected())
        {
          // start streaming audio
          JFileChooser c = new JFileChooser();
          c.setDialogTitle( "Save Audio" );
          c.setCurrentDirectory( currentDir );
          if (c.showSaveDialog( frame ) == JFileChooser.APPROVE_OPTION)
          {
            File saveAs = c.getSelectedFile();
            if (saveAs.exists())
              if (JOptionPane.showConfirmDialog( frame, "File exists, overwrite?" ) != JOptionPane.OK_OPTION)
                return;
            controlStreaming( true, saveAs );
            btnAudioRecord.setText( "STREAMING" );
          }
          currentDir = c.getCurrentDirectory();
        }
        else
        {
          // stop streaming audio
          btnAudioRecord.setText( "stream" );
          controlStreaming( false, null );
        }
      }
    });
    controls.add( btnAudioRecord );
    // MUTE
    btn_mute = new JToggleButton( "mute" );
    btn_mute.addActionListener( new ActionListener()
    {
      @Override
      public void actionPerformed( ActionEvent e )
      {
        wave.mute( btn_mute.isSelected() );
      }
    });
    wave.mute( false );
    controls.add( btn_mute );
    controls.add( lightBox );
    root.add( controls );
    // open it
    frame.setVisible( true );
    // make it closable
    frame.addWindowListener( new WindowAdapter() {
      @Override
      public void windowClosing( WindowEvent e )
      {
        wave.fade( true );
        new Thread("exit")
        {
          public void run()
          {
            try
            {
              Thread.sleep( 3000 );
            }
            catch( Exception x )
            {
            }
            System.exit( 0 );
          }
        }.start();
      }
    });
  }
  
  private JSON recordedSettings;
  private Thread recordingThread;

  public void controlRecording( boolean start )
  {
    if (start)
    {
      if (recordingThread != null)
        controlRecording( false );
      recordingThread = new Thread() {
        @Override
        public void run()
        {
          try
          {
            final double thresh = 50;
            JSON prev = null;
            long tPrev = System.currentTimeMillis();
            for (;;)
            {
              Thread.sleep( 100 );
              JSON settings = JSON.createObject();
              getSaveState( settings );
              if (prev == null)
                prev = settings;
              if (prev != null)
              {
                boolean changed = false;
                for (String key : settings.getFields())
                {
                  double vOld = prev.getDouble( key, 0 );
                  double vNew = settings.getDouble( key, 0 );
                  if (Math.abs( vOld - vNew ) > thresh)
                    changed = true;
                }
                if (changed)
                {
                  long tNow = System.currentTimeMillis();
                  double duration = (tNow - tPrev) / 1000.0;
                  tPrev = tNow;
                  settings.set( "duration", duration );
                  recordedSettings.addArrayElement( settings );
                  //TODO update button to show size of recording
                  btnRecord.setText( "RECORDING:" + recordedSettings.getArraySize() );
                  // compare against these settings
                  prev = settings;
                }
              }
            }
          }
          catch( InterruptedException x )
          {
          }
        }
      };
      recordingThread.start();
      recordedSettings = JSON.createArray();
    }
    else
    {
      if (recordingThread == null)
        return;
      recordingThread.interrupt();
      recordingThread = null;
    }
  }

  /**
   * Start/stop audio streaming.
   */
  private AudioFileEncoder audioEncoder;
  
  public void controlStreaming( boolean state, File output )
  {
    try
    {
      if (state)
      {
        audioEncoder = new AudioFileEncoder( wave.getRate(), output );
      }
      else
      {
        AudioFileEncoder encoder = audioEncoder;
        // stop sending data
        audioEncoder = null;
        // write out file
        //FIXME NEEDS PROGRESS BAR!!!
        encoder.close();
      }
    }
    catch( Exception x )
    {
      JOptionPane.showMessageDialog( frame, x );
    }
  }
  
  public void mute( boolean s )
  {
    btn_mute.setSelected( s );
    wave.mute( s );
  }
  public boolean isMuted()
  {
    return wave.isMuted();
  }
  JComponent decorateOther( String label, final JComponent component )
  {
    Box box = Box.createHorizontalBox();
    box.setBorder( BorderFactory.createEmptyBorder( 0/*T*/, 10/*L*/, 0/*B*/, 10/*R*/ ) );
    JLabel lbl = new JLabel( label );
    lbl.setPreferredSize( new Dimension( 120, lbl.getSize().height ) );
    box.add( lbl );
    box.add( component );
    String tag = label;
    tag = tag.replaceFirst( "([^:]*).*", "$1" );
    tag = tag.replaceFirst( "(.*)\\((.*)\\)", "$1_$2" );
    persisted.add( new PersistedValue( tag, component ) );
    return box;
  }
  JComponent decorateSlider( String label, final JSlider slider, final double factor, final boolean log, final SmoothValue v )
  {
    Box box = Box.createHorizontalBox();
    box.setBorder( BorderFactory.createEmptyBorder( 0/*T*/, 10/*L*/, 0/*B*/, 10/*R*/ ) );
    JLabel lbl = new JLabel( label );
    lbl.setPreferredSize( new Dimension( 120, lbl.getSize().height ) );
    box.add( lbl );
    final JLabel showValue = new JLabel( "..." );
    showValue.setPreferredSize( new Dimension( 60, showValue.getSize().height ) );
    box.add( slider );
    box.add( showValue );
    ChangeListener updateValue = new ChangeListener()
    {
      @Override
      public void stateChanged( ChangeEvent evt )
      {
        int vSlider = slider.getValue();
        double value = vSlider * factor;
        if (log)
          value = Math.exp( value );
        String str = String.format( "%.2f", value );
        showValue.setText( str );
        v.setValue( value, initialLoad );
      }
    };
    slider.addChangeListener( updateValue );
    updateValue.stateChanged( null );
    String tag = label;
    tag = tag.replaceFirst( "([^:]*).*", "$1" );
    tag = tag.replaceFirst( "(.*)\\((.*)\\)", "$1_$2" );
    persisted.add( new PersistedValue( tag, slider ) );
    return box;
  }
  protected Properties getSaveState()
  {
    Properties props = new Properties();
    for (PersistedValue v : persisted)
      v.save( props );
    return props;
  }
  protected void getSaveState( JSON json )
  {
    for (PersistedValue v : persisted)
      v.save( json );
  }
  protected void setSaveState( Properties props )
  {
    for (PersistedValue v : persisted)
      v.load( props );
  }
  protected void setSaveState( JSON props )
  {
    for (PersistedValue v : persisted)
      v.load( props );
  }

  static private boolean enableLog = false;
  static private long logT0 = System.nanoTime();
  static private PrintStream logStream;
  static private void log( String msg )
  {
    if (! enableLog)
      return;
    try
    {
      if (logStream == null)
      {
        String date = new SimpleDateFormat( "HHmm" ).format( new Date() );
        logStream = new PrintStream( "/Users/marklipson/Desktop/hypnotuner-debug-" + date + ".log" );
      }
      logStream.println( (System.nanoTime()-logT0)/1e9 + "\t" + msg );
      logStream.flush();
    }
    catch( Exception x )
    {
      x.printStackTrace( System.err );
    }
  }
  void playSome() throws Exception
  {
    // number of seconds
    double nSeconds = 0.1;
    double maxBufferLevel = 0.5; // starts clicking on some systems below 0.3
    if (wave.isFading())
      maxBufferLevel = 5; // we can buffer as much as we like now
    int bufferDelay = 10;
    log( speakers.getBufferLevel() + "\tdelay" );
    while (speakers.getBufferLevel() > maxBufferLevel)
    {
      Thread.sleep( bufferDelay );
      //System.out.println( "d: " + speakers.getBufferLevel() );
    }
    log( speakers.getBufferLevel() + "\tgenerate" );
    // values for loop
    int nSamples = (int)(wave.getRate() * nSeconds);
    float vLR[][] = wave.generate( nSamples );
    if (audioEncoder != null)
      audioEncoder.write( vLR[0], vLR[1] );
    //System.out.println( "first=" + vL[0] + ", last=" + vL[vL.length-1] );
    log( speakers.getBufferLevel() + "\tplay" );
    speakers.play( vLR[0], vLR[1] );
    log( speakers.getBufferLevel() + "\tdone" );
  }
    
  private void playContinuously()
  {
    Thread thread = new Thread("play")
    {
      @Override
      public void run()
      {
        while (! Thread.interrupted())
        {
          try
          {
            playSome();
          }
          catch( InterruptedException x )
          {
            break;
          }
          catch( Exception x )
          {
            x.printStackTrace( System.err );
          }
        }
      }
    };
    thread.start();
  }
  public static void main( String[] args )
  {
    File home = new File( System.getProperty( "user.home" ) );
    File settingsFile = new File( home, "hypnotuner.settings" );
    File defaultFolder = new File( home, "hypnotuner" );
    defaultFolder.mkdir();
    try
    {
      System.out.println( "HypnoTuner..." );
      HypnoTuner tuner = new HypnoTuner( settingsFile );
      tuner.currentDir = defaultFolder;
      tuner.buildFrame();
      tuner.restoreSettings();
      tuner.autoSaveSettings();
      tuner.setupSound();
      System.out.println( "starting" );
      tuner.playContinuously();
      tuner.pulseLight();
    }
    catch( Exception x )
    {
      x.printStackTrace( System.err );
    }
  }
}


//TODO auto-fade output files
//TODO default 'ogg' file extension for stream

//TODO store settings as real values, not internal integer values from sliders

//TODO separate options panel...
//  - anti-glitch options (or better, make it never click, or auto-adjust, etc.)
//  - whether to show lots of harmonics
//TODO limit view to relevant files?  default '.trance' file extension?


//speculative:
//TODO custom harmonics - choose a multiplier and an amplitude
//TODO separate L/R controls
//TODO math-script option - enter it in a text box: sin(t*30+sin(t*2)) - sin cos * + - / ^ (n) (t) () pi impl* -- autoscale
